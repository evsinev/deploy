---
title: Changelog
description: Notable user-facing changes per release. Full jars are attached to each GitHub release.
---

Notable user-facing changes per release. Each [GitHub release](https://github.com/evsinev/deploy/releases)
attaches the runnable jars (`deploy-server-<tag>.jar`, `deploy-agent-<tag>.jar`) — see
[Installation](/deploy/installation/#run-from-a-release).

## 1.0-25

**Dashboard SSE streams survive a reverse proxy**

The `/log/events` and `/agentlog/events` tails used to send nothing — not even the response
headers — until the first new line appeared, so nginx logged
`upstream timed out (110) while reading response header from upstream` every 60&nbsp;s on a quiet
server and the browser reconnected in a loop. All three dashboard streams now flush their headers
at once with a `: connected` comment, send a `: keepalive` comment after 15&nbsp;s of silence, and
set `X-Accel-Buffering: no`. See [Behind a reverse proxy](/deploy/guides/dashboard/#behind-a-reverse-proxy).

## 1.0-24

**Deploy review webhook at deploy start**
([release](https://github.com/evsinev/deploy/releases/tag/1.0-24))

A new outbound webhook notifies an external service the moment a deploy starts (the same moment as
the 🛫 Telegram message), so the release can be analysed while it is still being rolled out:

- Configured by [`DEPLOY_REVIEW_*`](/deploy/configuration/#deploy-review-webhook) — disabled by
  default, so nothing changes unless you set `DEPLOY_REVIEW_ENABLED=true`.
- Body is `{project, app, instance, old_version, new_version, deployed_at}`; the token travels in an
  `Authorization: Bearer` header and is never logged or spooled.
- `project`, `app` and `instance` come from three new fields in the alias
  [`diff:` block](/deploy/guides/writing-aliases/#deploy-review-webhook); the versions are the ones
  the diff already resolves, so the webhook fires only for aliases with `diff.enabled: true`.
- Delivery is durable and at-least-once through a third spool (`QUEUE_DIR/deploy-review`), with
  a `queue="deploy-review"` row on the dashboard **Delivery** card and in
  [the queue metrics](/deploy/reference/metrics/#queue-metrics). `4xx` answers are dead-lettered
  immediately instead of being retried.

## 1.0-23

**Group diff commit messages by date in Telegram and Redmine**
([release](https://github.com/evsinev/deploy/releases/tag/1.0-23))

The commit list posted during a deploy is now grouped by commit date instead of repeating the
date on every line (see the [Redmine workflow](/deploy/guides/redmine-workflow/#4-gitlab-diff-optional)):

- **Telegram** — each date appears once as a header line, with its commits listed as bullets
  underneath, newest day first.
- **Redmine** — the comment table now has `Issue | Subject` columns, with each date rendered as a
  colspan header row.
- Undated commits are grouped under a `(no date)` header, shown last.
- Long Telegram messages still split at ~4000 characters, and the date header is re-emitted at the
  top of each continuation message so bullets stay under their date.

## 1.0-22

**Keep the Delivery card visible as agent logs grow**
([release](https://github.com/evsinev/deploy/releases/tag/1.0-22))

Fixes a layout bug on the Live [dashboard](/deploy/guides/dashboard/): the **Delivery** card
(Telegram/Redmine queue depths and send-latency percentiles) could be squeezed down to just its
header as the agent-log stream accumulated, making the card look empty. The Delivery card now
keeps its full height and the agent-log pane scrolls internally.

This is a dashboard-only fix — no server behavior or configuration changed. The card content was
never wrong; it was simply being clipped by the layout.
