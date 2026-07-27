---
title: Live dashboard
description: The htmx/SSE dashboard — screens, live cards, and what each shows.
---

The server serves a live, server-rendered dashboard at `DASHBOARD_PATH` (default
`/deploy/dashboard`). It uses htmx with Server-Sent Events; the htmx assets are vendored, so it
works offline. Cards refresh every `DASHBOARD_REFRESH_MS` (default 2000&nbsp;ms).

## Screens

The top navigation has five screens plus a light/dark theme toggle:

- **Live** — the operational overview (cards described below).
- **Agents** — a per-agent table: name, ip, connection uptime, heap, agent-reported version,
  and connected/disconnected status.
- **Config** — the effective value of every [configuration variable](/deploy/configuration/) at
  runtime, with secrets masked.
- **Aliases** — the list of alias files and, for a selected alias, its commands
  (`agents` / `name` / `arguments`) plus the raw YAML.
- **Log** — a tail of the server log file (`SERVER_LOG_FILE`); shows an explicit reason when the
  file is unconfigured or empty.

## Live cards

The **Live** screen streams these cards over `{DASHBOARD_PATH}/events`:

- **Service** — process uptime, heap usage, connected-agent count.
- **Agents** — the connected agent ids.
- **Current task** — the most recently executed deploy task and its state.
- **Issues queue** — issue ids waiting to be processed (with an inline form to enqueue one).
- **Delivery** — the durable Redmine/Telegram queues: pending, dead, sent, dead-lettered counts
  and send-latency percentiles (p50/p95/p99).
- **Agent logs** — a live tail of recent command output from the agents.

## Enqueuing an issue

The **Issues queue** card has a form that `POST`s to `{DASHBOARD_PATH}/issue` with an `issue_id`
field, enqueuing the issue exactly as the webhook would. The endpoint is method-agnostic so a
reverse proxy may rewrite the POST to a GET without breaking it.

:::caution
The dashboard is not authenticated and can trigger deploys (enqueue issues). Keep it on loopback
or behind an authenticating reverse proxy.
:::
