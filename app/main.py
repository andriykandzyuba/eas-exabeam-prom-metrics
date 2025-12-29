from prometheus_client import Histogram, start_http_server
import time
import random
import threading

# Create a histogram metric to track request latencies
REQUEST_LATENCY = Histogram(
    'http_request_duration_seconds_bucket', 
    'HTTP request duration in seconds',
    buckets=(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, float('inf')) # Custom buckets
)

def generate_observations():
    # Produce random durations periodically so the histogram accumulates buckets
    while True:
        # simulate a request duration (seconds)
        dur = random.expovariate(1 / 0.1)
        REQUEST_LATENCY.observe(dur)
        time.sleep(random.uniform(0.01, 0.5))

if __name__ == '__main__':
    # Expose metrics on port 8000
    start_http_server(8000)
    t = threading.Thread(target=generate_observations, daemon=True)
    t.start()
    print('Metrics available at http://0.0.0.0:8000/metrics')
    try:
        while True:
            time.sleep(10)
    except KeyboardInterrupt:
        print('Shutting down')
