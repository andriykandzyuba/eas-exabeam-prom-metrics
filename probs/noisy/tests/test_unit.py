import json
import logging

import pytest

from app.main import Config, ConfigError, Emitter, JsonFormatter, Service, emit_error, emit_info


def test_config_defaults():
    cfg = Config.from_env({})
    assert (cfg.info_interval_ms, cfg.error_interval_ms, cfg.port) == (1000, 1000, 8080)


def test_config_from_env():
    cfg = Config.from_env({"LOG_INFO_INTERVAL_MS": "250", "LOG_ERROR_INTERVAL_MS": "50", "PORT": "9000"})
    assert (cfg.info_interval_ms, cfg.error_interval_ms, cfg.port) == (250, 50, 9000)


@pytest.mark.parametrize("bad", ["abc", "0", "-5", "1.5"])
def test_config_invalid_values(bad):
    with pytest.raises(ConfigError):
        Config.from_env({"LOG_INFO_INTERVAL_MS": bad})
    with pytest.raises(ConfigError):
        Config.from_env({"LOG_ERROR_INTERVAL_MS": bad})


def test_json_formatter_shape():
    record = logging.LogRecord("noisy.x", logging.ERROR, __file__, 1, "boom %s", ("!",), None)
    record.error_code = "E1"
    data = json.loads(JsonFormatter().format(record))
    assert data["level"] == "ERROR"
    assert data["message"] == "boom !"
    assert data["logger"] == "noisy.x"
    assert data["service"] == "noisy"
    assert data["error.code"] == "E1"
    assert data["timestamp"].endswith("Z")


def test_emit_functions_levels(caplog):
    with caplog.at_level(logging.INFO):
        emit_info()
        emit_error()
    levels = [r.levelname for r in caplog.records if r.name == "noisy.generator"]
    assert levels == ["INFO", "ERROR"]
    assert caplog.records[-1].error_code


def test_emitter_start_stop_idempotent():
    em = Emitter("t", 10, lambda: None)
    assert not em.running
    em.start()
    em.start()
    assert em.running
    em.stop()
    em.stop()
    assert not em.running


def test_emitter_survives_exceptions():
    calls = []

    def flaky():
        calls.append(1)
        raise RuntimeError("x")

    em = Emitter("t", 10, flaky)
    em.start()
    try:
        deadline = __import__("time").monotonic() + 3
        while len(calls) < 3 and __import__("time").monotonic() < deadline:
            __import__("time").sleep(0.01)
    finally:
        em.stop()
    assert len(calls) >= 3


def test_service_status_state_machine():
    svc = Service(Config(info_interval_ms=1000, error_interval_ms=500))
    assert svc.status() == {"error_logging": False, "info_interval_ms": 1000, "error_interval_ms": 500}
    svc.error.start()
    assert svc.status()["error_logging"] is True
    svc.stop()
    assert svc.status()["error_logging"] is False
