package com.dynatrace;

import com.dynatrace.trace.TraceFilter;
import com.sun.net.httpserver.HttpServer;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.exporter.httpserver.HTTPServer;
import io.prometheus.metrics.model.registry.PrometheusRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    private final static Counter totalRequests = Counter.builder()
            .name("esa_http_monitor_requests_total")
            .register();

    private final static Counter totalSuccessful = Counter.builder()
            .name("esa_http_monitor_requests_successful")
            .register();

    public static void main(String[] args) {
        Logger logger = LogManager.getLogger("Main");

        logger.info("Starting HTTP monitor!");

        // Default values from environment variables
        String testEndpoint = "https://example.com";
        int connectTimeoutSec = 10;
        int requestTimeoutSec = 10;
        int repeatIntervalSec = 60;
        int awaitTerminationSec = 30;
        int prometheusPort = 8000;
        int healthPort = 8080;
        List<String> headers = new ArrayList<>();

        // Simple argument parsing
        logger.info("Program arguments: {}", Arrays.toString(args));

        for (String arg : args) {
            if (arg.startsWith("--endpoint=")) {
                testEndpoint = arg.split("=")[1];
            } else if (arg.startsWith("--connectionTimeout=") || arg.startsWith("--connectTimeout=")) {
                connectTimeoutSec = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--requestTimeout=")) {
                requestTimeoutSec = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--repeatInterval=")) {
                repeatIntervalSec = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--awaitTermination=")) {
                awaitTerminationSec = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--prometheusPort=")) {
                prometheusPort = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--healthPort=")) {
                healthPort = Integer.parseInt(arg.split("=")[1]);
            } else if (arg.startsWith("--header=")) {
                String[] parts = arg.split("=")[1].split(":", 2);
                if (parts.length == 2) {
                    headers.add(parts[0]);
                    headers.add(parts[1]);
                }
            } else if (arg.startsWith("-")) {
                logger.warn("Unrecognized argument: {}. If this is a container engine flag (like -p or -e), it must be placed BEFORE the image name in the 'podman run' or 'docker run' command.", arg);
            }
        }

        if(headers.isEmpty()) {
            headers.add("Accept");
            headers.add("application/json");
        }

        // Print parameters
        logger.info("Monitoring URI: {}", testEndpoint);
        logger.info("Connect Timeout: {} seconds", connectTimeoutSec);
        logger.info("Request Timeout: {} seconds", requestTimeoutSec);
        logger.info("Repeat Interval: {} seconds", repeatIntervalSec);
        logger.info("Await Termination: {} seconds", awaitTerminationSec);
        logger.info("Prometheus Port: {}", prometheusPort);
        logger.info("Health Port: {}", healthPort);

        PrometheusRegistry prometheusRegistry = PrometheusRegistry.defaultRegistry;

        HTTPServer prometheusServer;
        HttpServer healthServer;
        try {
            prometheusServer = HTTPServer.builder()
                    .port(prometheusPort)
                    .registry(prometheusRegistry)
                    .buildAndStart();
            healthServer = HttpServer.create(new InetSocketAddress(healthPort), 0);
        } catch (IOException e) {
            logger.error("Failed to start HTTP servers: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        healthServer.createContext("/health", httpExchange -> {
            String response = "OK";
            httpExchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = httpExchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        healthServer.start();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSec))
                .build();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofVirtual().factory()
        );

        CountDownLatch shutdownLatch = new CountDownLatch(1);

        final int finalAwaitTerminationSec = awaitTerminationSec;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown signal received...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(finalAwaitTerminationSec, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
            client.close();
            prometheusServer.stop();
            healthServer.stop(0);
            shutdownLatch.countDown();
            logger.info("Shutdown complete.");
        }));

        final String finalTestEndpoint = testEndpoint;
        final int finalRequestTimeoutSec = requestTimeoutSec;
        final List<String> finalHeaders = headers;

        try {
            // 3. Schedule the task: initial delay of 0s
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    totalRequests.inc();

                    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                            .uri(URI.create(finalTestEndpoint))
                            .timeout(Duration.ofSeconds(finalRequestTimeoutSec))
                            .header("traceparent", TraceFilter.generateTraceparent());

                    for (int i = 0; i < finalHeaders.size(); i += 2) {
                        requestBuilder.header(finalHeaders.get(i), finalHeaders.get(i + 1));
                    }

                    HttpRequest request = requestBuilder.build();

                    HttpResponse<String> response = client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        totalSuccessful.inc();
                    }

                    logger.info("Status: {}, Response: {}", response.statusCode(), response.body());

                } catch (Exception e) {
                    logger.error("Request failed: {}", e.getMessage(), e);
                }
            }, 0, repeatIntervalSec, TimeUnit.SECONDS);

            logger.info("Monitor running for {} with interval {}s. Press Ctrl+C to stop.", testEndpoint, repeatIntervalSec);
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            scheduler.shutdownNow();
            client.close();
            prometheusServer.stop();
            healthServer.stop(0);
        }
    }
}
