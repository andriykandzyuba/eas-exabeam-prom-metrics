package metrics

import (
	"context"
	"math/rand"
	"time"

	"fmt"

    "github.com/prometheus/client_golang/prometheus"
    "github.com/prometheus/client_golang/prometheus/promauto"
)

// 1. Define custom metrics using promauto for automatic registry placement
var (
	// Counter tracks the total number of events (only increases)
	simulatedRequestsTotal = promauto.NewCounterVec(
		prometheus.CounterOpts{
			Name: "simulated_requests_total",
			Help: "Total number of simulated HTTP requests.",
		},
		[]string{"endpoint", "status"},
	)

	// Histogram tracks the distribution of event durations (latencies)
	simulatedRequestDuration = promauto.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "simulated_request_duration_seconds",
			Help:    "Simulated HTTP request latency distribution.",
			Buckets: []float64{0.1, 0.25, 0.5, 1.0, 2.5, 5.0}, // Custom latency buckets
		},
		[]string{"endpoint"},
	)
)

// RunMetricsSimulation loops to mimic real application traffic until the context is cancelled
func RunMetricsSimulation(ctx context.Context) {
	endpoints := []string{"/api/v1/users", "/api/v1/products", "/api/v1/checkout"}
	statuses := []string{"200", "200", "200", "400", "500"} // Weighted toward success

	for {
		select {
		case <-ctx.Done():
			return
		default:
			// Pick random values for simulation
			endpoint := endpoints[rand.Intn(len(endpoints))]
			status := statuses[rand.Intn(len(statuses))]

			// Simulate latency between 50ms and 1500ms
			fakeLatency := float64(rand.Intn(1450)+50) / 1000.0

            fmt.Printf("Simulating metrics")

			// Update metrics
			simulatedRequestsTotal.WithLabelValues(endpoint, status).Inc()
			simulatedRequestDuration.WithLabelValues(endpoint).Observe(fakeLatency)

			// Wait briefly before generating the next fake request event
			// Using a timer to make the sleep interruptible
			timer := time.NewTimer(time.Duration(rand.Intn(400)+100) * time.Millisecond)
			select {
			case <-ctx.Done():
				timer.Stop()
				return
			case <-timer.C:
			}
		}
	}
}