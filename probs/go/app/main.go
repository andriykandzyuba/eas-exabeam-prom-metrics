package main

import (
	"app/pkg/otel"
	"fmt"
)

var status string

func init() {
	fmt.Println("Initializing application...")
	otel.InitOpenTelemetry()
	status = "Ready"
}

func main() {
	fmt.Println("Application Status:", status)
	greeting := otel.Hello("World")
    fmt.Println(greeting)
}


