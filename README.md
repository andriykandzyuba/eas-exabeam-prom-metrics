# Example: Python app exposing Prometheus histogram buckets, scraped by OpenTelemetry Collector

Files added:
- `app/main.py` - Python process that exposes a Prometheus Histogram (creates `*_bucket` metrics).
- `Dockerfile` - Build image for the Python app.
- `requirements.txt` - Python dependency (`prometheus_client`).
- `otel-collector-config.yaml` - OTEL Collector config with Prometheus receiver scraping `app:8000`.
- `docker-compose.yml` - Runs the app and the OTEL Collector locally.

Quick start

1. Build and run both services:

```bash
docker compose up --build
```

2. Confirm the app metrics endpoint is reachable:

```bash
curl http://localhost:8000/metrics | head -n 20
```

You should see lines like `http_request_duration_seconds_bucket{le="0.005"} ...` — the `_bucket` timeseries produced by the histogram.

3. Check OTEL Collector logs to see scraped metrics (the `logging` exporter prints metrics):

```bash
docker logs -f otel_collector
```

Notes
- The collector's `prometheus` receiver scrapes the Docker Compose service name `app:8000`.
- Adjust scrape interval and targets in `otel-collector-config.yaml` if needed.
