---
title: HTTP API
description: Every HTTP endpoint the server exposes.
---

All endpoints are served by the single Vert.x server on `127.0.0.1:<VERTX_SERVER_PORT>`
(default port 8080).

:::danger
No endpoint is authenticated, and the `run` and `issue` commands and the dashboard action can
trigger deploys. Keep the server on loopback or behind an authenticating reverse proxy.
:::

| Method | Path | Description |
|---|---|---|
| POST | `REDMINE_CALLBACK_URI` (exact) | Redmine webhook; JSON body `{"issue_id": <long>}` enqueues an issue. |
| GET | `DASHBOARD_PATH` | Live [dashboard](/deploy/guides/dashboard/) HTML shell. |
| GET | `DASHBOARD_PATH/events` | SSE stream (cards: service, agents, status, issues, delivery queues, latency). |
| GET | `DASHBOARD_PATH/htmx.min.js`, `/sse.js` | Vendored htmx assets (offline). |
| POST | `DASHBOARD_PATH/issue` | Form field `issue_id` → enqueue; returns the refreshed list. |
| GET | `STATUS_PAGE_PATH` (prefix, default `/deploy/status`) | JSON `{connectedAgents, issueQueue, taskStatus}`. |
| GET | `/metrics` (exact) | Prometheus exposition. See [Metrics](/deploy/reference/metrics/). |
| GET | `/?command=listAgents` | List connected agents. |
| GET | `/?command=run&alias=<alias …>` | Parse and run an [alias](/deploy/guides/writing-aliases/) asynchronously. |
| GET | `/?command=issue&issue_id=<long>` | Enqueue an issue. |

The dashboard also exposes a few htmx fragment endpoints under `DASHBOARD_PATH`
(`/config`, `/aliases`, `/alias?name=…`, `/log`, `/log/events`, `/agentlog`, `/agentlog/events`)
that return HTML fragments for the corresponding screens.
