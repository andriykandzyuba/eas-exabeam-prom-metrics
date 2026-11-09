# Noisy logs simulator

  ## Goal
  Build a small Python service that emits a steady stream of INFO logs and, on demand, an additional stream of ERROR logs. It is the log source for a Bindplane + Dynatrace dynamic log ingestion/sampling proof of concept: operators
  flip the error stream on and off over HTTP and watch how the pipeline reacts to the volume change.

  Before writing code, read `../python` and reuse its project layout, logging configuration and Dockerfile conventions. Summarize in 3-5 bullets what you are reusing, then implement.

  ## Technical constraints
  - Python 3.9+ (do not use syntax newer than 3.9); Docker 20.10+.
  - Use Flask | FastAPI — pick whatever `../python` uses for the HTTP API; keep dependencies minimal and pinned in `requirements.txt`.
  - Listen on `0.0.0.0:${PORT:-8080}`.

  ## Behavior

  ### Log generation
  - INFO logs are emitted continuously from startup every `LOG_INFO_INTERVAL_MS` (default `1000`).
  - ERROR logs are emitted every `LOG_ERROR_INTERVAL_MS` (default `1000`) only while "escalated". The service starts de-escalated.
  - The two streams are independent: INFO never pauses, and changing the error state never resets or delays INFO timing.
  - Run each generator on its own background thread (or asyncio task). Intervals must not drift, so schedule against a monotonic clock. Shut down cleanly on SIGTERM/SIGINT.
  - Invalid interval values (non-integer, <= 0) must fail fast at startup with a clear message, or fall back to the default with a WARNING. Pick one and document it.

  ### Log format
  - Write to stdout, one JSON object per line, with at least: `timestamp` (ISO 8601 UTC), `level`, `message`, `logger`, `service` (`noisy`).
  - Use a small pool of varied, realistic messages (e.g. DB timeout, upstream 503, payload validation failure) chosen at random, so the logs are not a single repeated line. ERROR messages may include a synthetic `error.code`.
  - Keep the logging config consistent with `../python`.

  ### HTTP API
  | Method | Path | Behavior | Response |
  |---|---|---|---|
  | POST | `/logs/escalate` | Start the ERROR stream; idempotent | `200` `{"error_logging": true}` |
  | POST | `/logs/descalate` | Stop the ERROR stream; idempotent | `200` `{"error_logging": false}` |
  | GET | `/logs/status` | Current state and configured intervals | `200` JSON |
  | GET | `/healthz` | Liveness probe | `200` |

  Other methods on these paths return `405`. Toggling the state must be thread-safe.

  ## Structure
  ```
  probs/noisy/
    app/main.py            # entrypoint: config, generators, API
    tests/                 # unit + integration tests
    Dockerfile             # slim base, non-root user, EXPOSE port, runs app/main.py
    requirements.txt
    README.md              # run, configure, curl examples
  ```

  ## Testing (pytest)
  - **Unit:** config parsing and defaults, invalid values, log record shape, and the escalate/descalate state machine, including idempotency.
  - **Integration:** use the framework's test client against the API. Run the generators with short intervals (e.g. 20-50 ms) or an injectable clock and capture the log output. Assert that:
    - INFO logs flow before any escalation and contain no ERROR records.
    - ERROR records appear after `escalate` and stop after `descalate`.
    - INFO continues throughout.
  - Tests must be deterministic: poll with a timeout instead of fixed `sleep` assertions, and never rely on wall-clock precision.

  ## Acceptance criteria
  1. `docker build` and `docker run -e LOG_INFO_INTERVAL_MS=500 -p 8080:8080 ...` start the service and print INFO JSON logs.
  2. `curl -X POST localhost:8080/logs/escalate` makes ERROR logs appear at the configured rate.
  3. `curl -X POST localhost:8080/logs/descalate` stops them, with INFO uninterrupted.
  4. `pytest` passes locally and exits cleanly with no leaked threads.
  5. The README documents all env vars, endpoints and example commands.

  ## Out of scope
  Authentication, persistence, metrics endpoints, and log shipping (the Bindplane agent handles collection).

    - ERROR records appear after `escalate` and stop after `descalate`.
    - INFO continues throughout.
  - Tests must be deterministic: poll with a timeout instead of fixed `sleep` assertions, and never rely on wall-clock precision.

  ## Acceptance criteria
  1. `docker build` and `docker run -e LOG_INFO_INTERVAL_MS=500 -p 8080:8080 ...` start the service and print INFO JSON logs.
  2. `curl -X POST localhost:8080/logs/escalate` makes ERROR logs appear at the configured rate.
  3. `curl -X POST localhost:8080/logs/descalate` stops them, with INFO uninterrupted.
  4. `pytest` passes locally and exits cleanly with no leaked threads.
  5. The README documents all env vars, endpoints and example commands.

  ## Out of scope
  Authentication, persistence, metrics endpoints, and log shipping (the Bindplane agent handles collection).

  ## Working style
  Make small, reasonable decisions yourself and list them at the end under "Assumptions". Don't stop to ask unless something blocks you.