# Stage 1: Build the Go application
FROM golang:1.25-alpine AS builder

WORKDIR /app

# Copy go.mod and go.sum files (if they exist)
COPY app/go.mod* app/go.sum* ./
RUN if [ -f go.mod ]; then go mod download; fi

# Copy the source code
COPY app/ .

# Build the application
RUN go build -o main .

# Stage 2: Final runtime image
FROM alpine:latest

WORKDIR /app

# Create and switch to a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the binary from the builder stage
COPY --from=builder /app/main .

EXPOSE 8080 8000

CMD ["./main"]
