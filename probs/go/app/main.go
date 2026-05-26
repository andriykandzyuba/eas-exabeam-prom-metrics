package main

import (
	"app/pkg/otel"
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

var status string

func init() {
	fmt.Println("Initializing application...")
	otel.InitOpenTelemetry()
	status = "Ready"
}

func main() {
	fmt.Println("Application Status:", status)

	mux := http.NewServeMux()
	mux.HandleFunc("/greetings/v1/hello", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/plain")
		fmt.Fprint(w, "Hello, World!")
	})

	server := &http.Server{
		Addr:    ":8080",
		Handler: mux,
	}

	// Metrics server on port 8000 (mocking metrics for now)
	metricsMux := http.NewServeMux()
	metricsMux.HandleFunc("/metrics", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "text/plain")
		fmt.Fprint(w, "# HELP http_requests_total Total number of HTTP requests\n")
		fmt.Fprint(w, "# TYPE http_requests_total counter\n")
		fmt.Fprint(w, "http_requests_total 0\n")
	})

	metricsServer := &http.Server{
		Addr:    ":8000",
		Handler: metricsMux,
	}

	// Channel to listen for termination signals
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)

	go func() {
		fmt.Println("HTTP Server starting on port 8080...")
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			fmt.Printf("HTTP Server failed: %s\n", err)
			os.Exit(1)
		}
	}()

	go func() {
		fmt.Println("Metrics Server starting on port 8000...")
		if err := metricsServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			fmt.Printf("Metrics Server failed: %s\n", err)
		}
	}()

	// Block until a signal is received
	sig := <-stop
	fmt.Printf("Received signal: %v. Shutting down gracefully...\n", sig)

	// Context with timeout for the shutdown process
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	// Shutdown both servers
	server.Shutdown(ctx)
	metricsServer.Shutdown(ctx)

	fmt.Println("Servers exited gracefully")
}


