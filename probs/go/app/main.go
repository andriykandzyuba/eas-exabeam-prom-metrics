package main

import (
	"app/pkg/api"
	"app/pkg/metrics"
	"app/pkg/otel"
	"context"
	"fmt"
	"os"
	"os/signal"
	"syscall"
	"time"
)

var (
	apiServer     *api.Server
	metricsServer *metrics.Server
)

func init() {
	fmt.Println("Initializing application...")

	otel.InitOpenTelemetry()

	apiServer = api.NewServer(8080)
	metricsServer = metrics.NewServer(8000)

    go func() {
		if err := apiServer.Start(); err != nil {
			fmt.Printf("HTTP Server failed: %s\n", err)
			os.Exit(1)
		}
	}()

	go func() {
		if err := metricsServer.Start(); err != nil {
			fmt.Printf("Metrics Server failed: %s\n", err)
		}
	}()

    fmt.Println("Starting background metrics simulation...")
}

func main() {
	// Channel to listen for termination signals
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)

	// Context with timeout for the shutdown process
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer shutdownCancel()

	// Context for simulation and other background tasks
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// Start metrics simulation
	go metrics.RunMetricsSimulation(ctx)

    // Wait for termination signal
	// Defer shutdown of both servers
    defer func() {
        fmt.Println("Shutting down servers...")
        cancel() // Stop simulation
        apiServer.Shutdown(shutdownCtx)
        metricsServer.Shutdown(shutdownCtx)
        fmt.Println("Servers exited gracefully")
    }()

	// Block until a signal is received
	sig := <-stop
	fmt.Printf("Received signal: %v. Initiating graceful shutdown...\n", sig)
}

