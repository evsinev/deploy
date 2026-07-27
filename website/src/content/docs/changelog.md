---
title: Changelog
description: Notable user-facing changes per release. Full jars are attached to each GitHub release.
---

Notable user-facing changes per release. Each [GitHub release](https://github.com/evsinev/deploy/releases)
attaches the runnable jars (`deploy-server-<tag>.jar`, `deploy-agent-<tag>.jar`) — see
[Installation](/deploy/installation/#run-from-a-release).

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
