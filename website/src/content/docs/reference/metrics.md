---
title: Metrics
description: Prometheus metrics exposed at /metrics.
---

The server exposes Prometheus metrics at `/metrics` (exact path).

## Queue metrics

One series per durable queue, tagged `queue="telegram"` or `queue="redmine"`:

| Metric | Type | Meaning |
|---|---|---|
| `deploy_queue_pending` | gauge | Operations spooled and not yet sent. |
| `deploy_queue_dead` | gauge | Operations currently in the `dead/` sub-directory. |
| `deploy_queue_sent_total` | counter | Operations successfully sent since start. |
| `deploy_queue_deadlettered_total` | counter | Operations moved to dead-letter since start. |
| `deploy_queue_send_latency` | timer histogram | Latency of a successful send/edit call (p50/p95/p99). |

These are the same numbers shown on the dashboard's [Delivery card](/deploy/guides/dashboard/#live-cards).

## JVM & process metrics

Standard Micrometer bindings are also registered: JVM memory, garbage collection, threads, and
process (CPU, file descriptors, uptime) metrics.

:::note
Micrometer is pinned to `1.12.6` so the `io.micrometer.prometheus.*` package
(`PrometheusConfig`, `PrometheusMeterRegistry`) still exists; 1.13+ relocated those classes.
:::
