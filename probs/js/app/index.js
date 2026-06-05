const express = require('express');
const { Registry, Counter, Histogram, collectDefaultMetrics } = require('prom-client');

// Configuration
const API_PORT = 8080;
const METRICS_PORT = 8000;

// --- Metrics Setup ---
const register = new Registry();
collectDefaultMetrics({ register });

const simulatedRequestsTotal = new Counter({
  name: 'exa_requests_total',
  help: 'Total number of simulated HTTP requests.',
  labelNames: ['endpoint', 'status'],
  registers: [register],
});

const simulatedRequestDuration = new Histogram({
  name: 'exa_request_duration_seconds',
  help: 'Simulated HTTP request latency distribution.',
  labelNames: ['endpoint'],
  buckets: [0.1, 0.25, 0.5, 1.0, 2.5, 5.0],
  registers: [register],
});

// --- API Server (Port 8080) ---
const apiApp = express();

apiApp.get('/greetings/v1/hello', (req, res) => {
  res.set('Content-Type', 'text/plain');
  res.send('Hello, World!');
});

const apiServer = apiApp.listen(API_PORT, () => {
  console.log(`API Server listening on port ${API_PORT}`);
});

// --- Metrics Server (Port 8000) ---
const metricsApp = express();

metricsApp.get('/metrics', async (req, res) => {
  try {
    res.set('Content-Type', register.contentType);
    res.end(await register.metrics());
  } catch (err) {
    res.status(500).end(err.message);
  }
});

const metricsServer = metricsApp.listen(METRICS_PORT, () => {
  console.log(`Metrics Server listening on port ${METRICS_PORT}`);
});

// --- Metrics Simulation ---
const endpoints = ['/api/v1/users', '/api/v1/products', '/api/v1/checkout'];
const statuses = ['200', '200', '200', '400', '500'];

function runSimulation() {
  const endpoint = endpoints[Math.floor(Math.random() * endpoints.length)];
  const status = statuses[Math.floor(Math.random() * statuses.length)];
  const fakeLatency = (Math.floor(Math.random() * 1450) + 50) / 1000.0;

  simulatedRequestsTotal.inc({ endpoint, status });
  simulatedRequestDuration.observe({ endpoint }, fakeLatency);

  const delay = Math.floor(Math.random() * 400) + 100;
  setTimeout(runSimulation, delay);
}

runSimulation();

// --- Graceful Shutdown ---
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);

function shutdown() {
  console.log('Received termination signal. Initiating graceful shutdown...');
  apiServer.close(() => {
    console.log('API Server closed.');
    metricsServer.close(() => {
      console.log('Metrics Server closed.');
      process.exit(0);
    });
  });
}
