# Noisy logs simulator

Log source for the Bindplane + Dynatrace dynamic log ingestion/sampling PoC. Emits INFO logs continuously and, on demand, an additional ERROR stream.

## Run

```bash
docker build -t noisy .
docker run --rm -p 8080:8080 -e LOG_INFO_INTERVAL_MS=500 noisy
```

Locally: `pip install -r requirements.txt && python app/main.py` (Python 3.9+).

## Configuration

| Variable | Default | Description |
|---|---|---|
| `LOG_INFO_INTERVAL_MS` | `1000` | Interval between INFO logs |
| `LOG_ERROR_INTERVAL_MS` | `1000` | Interval between ERROR logs while escalated |
| `PORT` | `8080` | HTTP port |

Invalid values (non-integer or <= 0) make the service exit with code 2 and an error log.

## API

| Method | Path | Description |
|---|---|---|
| POST | `/logs/escalate` | Start ERROR logs (idempotent) → `{"error_logging": true}` |
| POST | `/logs/descalate` | Stop ERROR logs (idempotent) → `{"error_logging": false}` |
| GET | `/logs/status` | Current state and intervals |
| GET | `/healthz` | Liveness probe |

```bash
curl -X POST localhost:8080/logs/escalate
curl -X POST localhost:8080/logs/descalate
curl localhost:8080/logs/status
```

## Log format

JSON, one object per line, on stdout:

```json
{"timestamp": "2026-11-09T16:00:00.123Z", "level": "ERROR", "message": "Database query timed out after 30000ms", "logger": "noisy.generator", "service": "noisy", "error.code": "DB_TIMEOUT"}
```

## Tests

```bash
pip install -r requirements-dev.txt
pytest
```
