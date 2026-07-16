package com.dynatrace;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
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
            if (arg.startsWith("--endpoint=") || arg.startsWith("--uri=")) {
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

        PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        Counter totalRequests = Counter.builder("esa.http.monitor.total_requests")
                .register(prometheusRegistry);

        Counter totalSuccessful = Counter.builder("esa.http.monitor.total_successful")
                .register(prometheusRegistry);

        HttpServer prometheusServer;
        HttpServer healthServer;
        try {
            prometheusServer = HttpServer.create(new InetSocketAddress(prometheusPort), 0);
            healthServer = HttpServer.create(new InetSocketAddress(healthPort), 0);
        } catch (IOException e) {
            logger.error("Failed to start HTTP servers: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        prometheusServer.createContext("/metrics", httpExchange -> {
            String response = prometheusRegistry.scrape();
            httpExchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = httpExchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        healthServer.createContext("/health", httpExchange -> {
            String response = "OK";
            httpExchange.sendResponseHeaders(200, response.getBytes().length);
            try (OutputStream os = httpExchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        });

        prometheusServer.start();
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
            prometheusServer.stop(0);
            healthServer.stop(0);
            shutdownLatch.countDown();
            logger.info("Shutdown complete.");
        }));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(testEndpoint))
                    .headers(headers.toArray(new String[0]))
                    .timeout(Duration.ofSeconds(requestTimeoutSec))
                    .build();

            // 3. Schedule the task: initial delay of 0s
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    totalRequests.increment();
                    HttpResponse<String> response = client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        totalSuccessful.increment();
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
            prometheusServer.stop(0);
            healthServer.stop(0);
        }
    }
}
