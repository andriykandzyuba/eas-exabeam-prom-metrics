package com.dynatrace.trace;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class TraceFilter extends Filter {

    private static final Logger logger = LogManager.getLogger(TraceFilter.class);

    @Override
    public String description() {
        return "Injects W3C Traceparent / Tracestate headers";
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        Headers reqHeaders = exchange.getRequestHeaders();
        String incomingTraceparent = reqHeaders.getFirst("traceparent");

        String traceId;
        String parentSpanId;
        String traceFlags = "01"; // Recorded

        if (incomingTraceparent != null && !incomingTraceparent.isEmpty()) {
            // Parse existing W3C traceparent (format: version-trace_id-parent_id-trace_flags)
            String[] parts = incomingTraceparent.split("-");
            if (parts.length >= 3) {
                traceId = parts[1];
                // Make the current span the child of the previous span
                parentSpanId = generateRandomHex(16);
            } else {
                // Fallback to new if malformed
                traceId = generateRandomHex(32);
                parentSpanId = generateRandomHex(16);
            }
        } else {
            // Create brand new root W3C Trace Context
            traceId = generateRandomHex(32);
            parentSpanId = generateRandomHex(16);
        }

        // Print trace context
        logger.info("Trace Context - Trace ID: {}, Parent Span ID: {}, Trace Flags: {}", traceId, parentSpanId, traceFlags);

        // Construct the outgoing W3C traceparent
        String outgoingTraceparent = String.format("00-%s-%s-%s", traceId, parentSpanId, traceFlags);

        // Add the traceparent to the response headers
        Headers resHeaders = exchange.getResponseHeaders();
        resHeaders.add("traceparent", outgoingTraceparent);

        // Pass execution to the next filter/handler
        chain.doFilter(exchange);
    }

    public static String generateTraceparent() {
        String traceId = generateRandomHex(32);
        String parentSpanId = generateRandomHex(16);
        String traceFlags = "01"; // Recorded
        return String.format("00-%s-%s-%s", traceId, parentSpanId, traceFlags);
    }

    private static String generateRandomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        String chars = "0123456789abcdef";
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }
}