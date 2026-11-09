"""Noisy logs simulator.

Emits a steady stream of INFO logs and, on demand (via HTTP), an additional
stream of ERROR logs. Used as a log source for the Bindplane + Dynatrace
dynamic log ingestion/sampling proof of concept.
"""
import json
import logging
import os
import random
import signal
import sys
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Callable, Mapping, Optional

from flask import Flask, jsonify

SERVICE_NAME = "noisy"
DEFAULT_INTERVAL_MS = 1000
DEFAULT_PORT = 8080

INFO_MESSAGES = [
    "Heartbeat: service is healthy",
    "Processed batch of events",
    "Cache refreshed successfully",
    "Order accepted for processing",
    "Scheduled job completed",
    "User session established",
]

ERROR_MESSAGES = [
    ("DB_TIMEOUT", "Database query timed out after 30000ms"),
    ("UPSTREAM_503", "Upstream service returned HTTP 503 Service Unavailable"),
    ("VALIDATION_FAILED", "Payload validation failed: missing required field 'orderId'"),
    ("CONN_RESET", "Connection reset by peer while calling payment gateway"),
    ("QUEUE_FULL", "Unable to enqueue message: queue capacity exceeded"),
    ("NPE", "Unhandled exception: NoneType object has no attribute 'id'"),
]


# --------------------------------------------------------------------------
# Configuration
# --------------------------------------------------------------------------
class ConfigError(ValueError):
    """Raised when an environment variable holds an invalid value."""


def _positive_int(env: Mapping[str, str], name: str, default: int) -> int:
    raw = env.get(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        value = int(raw)
    except ValueError:
        raise ConfigError(f"{name} must be an integer, got {raw!r}") from None
    if value <= 0:
        raise ConfigError(f"{name} must be > 0, got {value}")
    return value


@dataclass(frozen=True)
class Config:
    info_interval_ms: int = DEFAULT_INTERVAL_MS
    error_interval_ms: int = DEFAULT_INTERVAL_MS
    port: int = DEFAULT_PORT

    @classmethod
    def from_env(cls, env: Optional[Mapping[str, str]] = None) -> "Config":
        env = os.environ if env is None else env
        return cls(
            info_interval_ms=_positive_int(env, "LOG_INFO_INTERVAL_MS", DEFAULT_INTERVAL_MS),
            error_interval_ms=_positive_int(env, "LOG_ERROR_INTERVAL_MS", DEFAULT_INTERVAL_MS),
            port=_positive_int(env, "PORT", DEFAULT_PORT),
        )


# --------------------------------------------------------------------------
# Logging
# --------------------------------------------------------------------------
class JsonFormatter(logging.Formatter):
    """One JSON object per line."""

    def format(self, record: logging.LogRecord) -> str:
        ts = datetime.fromtimestamp(record.created, tz=timezone.utc)
        payload = {
            "timestamp": ts.isoformat(timespec="milliseconds").replace("+00:00", "Z"),
            "level": record.levelname,
            "message": record.getMessage(),
            "logger": record.name,
            "service": SERVICE_NAME,
        }
        error_code = getattr(record, "error_code", None)
        if error_code:
            payload["error.code"] = error_code
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        return json.dumps(payload)


def configure_logging() -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    root = logging.getLogger()
    root.handlers[:] = [handler]
    root.setLevel(logging.INFO)
    # Keep HTTP access logs out of the simulated INFO stream.
    logging.getLogger("werkzeug").setLevel(logging.WARNING)


gen_logger = logging.getLogger("noisy.generator")
app_logger = logging.getLogger("noisy.app")


def emit_info() -> None:
    gen_logger.info(random.choice(INFO_MESSAGES))


def emit_error() -> None:
    code, message = random.choice(ERROR_MESSAGES)
    gen_logger.error(message, extra={"error_code": code})


# --------------------------------------------------------------------------
# Periodic emitters
# --------------------------------------------------------------------------
class Emitter:
    """Calls `fn` every `interval_ms` on a background thread.

    Deadlines are scheduled on the monotonic clock so the rate does not drift.
    start() and stop() are idempotent and thread-safe.
    """

    def __init__(self, name: str, interval_ms: int, fn: Callable[[], None]):
        self.name = name
        self._interval = interval_ms / 1000.0
        self._fn = fn
        self._lock = threading.Lock()
        self._stop_event: Optional[threading.Event] = None
        self._thread: Optional[threading.Thread] = None

    @property
    def running(self) -> bool:
        with self._lock:
            return self._thread is not None

    def start(self) -> None:
        with self._lock:
            if self._thread is not None:
                return
            stop_event = threading.Event()
            thread = threading.Thread(
                target=self._run, args=(stop_event,), name=f"emitter-{self.name}", daemon=True
            )
            self._stop_event, self._thread = stop_event, thread
            thread.start()

    def stop(self) -> None:
        with self._lock:
            stop_event, thread = self._stop_event, self._thread
            self._stop_event = self._thread = None
        if stop_event is not None and thread is not None:
            stop_event.set()
            thread.join(timeout=5)

    def _run(self, stop_event: threading.Event) -> None:
        next_at = time.monotonic()
        while not stop_event.is_set():
            try:
                self._fn()
            except Exception:  # never let the generator thread die silently
                app_logger.exception("Emitter %s failed", self.name)
            next_at += self._interval
            delay = next_at - time.monotonic()
            if delay < 0:  # fell behind; resync instead of bursting
                next_at = time.monotonic()
                delay = 0
            stop_event.wait(delay)


class Service:
    """Owns the INFO and ERROR emitters."""

    def __init__(self, config: Config):
        self.config = config
        self.info = Emitter("info", config.info_interval_ms, emit_info)
        self.error = Emitter("error", config.error_interval_ms, emit_error)

    def start(self) -> None:
        self.info.start()

    def stop(self) -> None:
        self.error.stop()
        self.info.stop()

    def status(self) -> dict:
        return {
            "error_logging": self.error.running,
            "info_interval_ms": self.config.info_interval_ms,
            "error_interval_ms": self.config.error_interval_ms,
        }


# --------------------------------------------------------------------------
# HTTP API
# --------------------------------------------------------------------------
def create_app(service: Service) -> Flask:
    app = Flask(__name__)

    @app.post("/logs/escalate")
    def escalate():
        service.error.start()
        app_logger.info("Error log generation escalated")
        return jsonify({"error_logging": True})

    @app.post("/logs/descalate")
    def descalate():
        service.error.stop()
        app_logger.info("Error log generation de-escalated")
        return jsonify({"error_logging": False})

    @app.get("/logs/status")
    def status():
        return jsonify(service.status())

    @app.get("/healthz")
    def healthz():
        return jsonify({"status": "ok"})

    return app


def main() -> int:
    configure_logging()
    try:
        config = Config.from_env()
    except ConfigError as exc:
        app_logger.error("Invalid configuration: %s", exc)
        return 2

    service = Service(config)
    app = create_app(service)

    def _shutdown(signum, _frame):
        app_logger.info("Received signal %s, shutting down", signum)
        service.stop()
        sys.exit(0)

    signal.signal(signal.SIGTERM, _shutdown)
    signal.signal(signal.SIGINT, _shutdown)

    app_logger.info(
        "Starting noisy logs simulator (info=%dms, error=%dms, port=%d)",
        config.info_interval_ms, config.error_interval_ms, config.port,
    )
    service.start()
    app.run(host="0.0.0.0", port=config.port, threaded=True, use_reloader=False)
    service.stop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
