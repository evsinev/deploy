---
title: Redmine deploy workflow
description: How a Redmine issue drives validation, a GitLab diff, the deploy, and status transitions.
---

Deployments are normally driven from **Redmine** issues. This page walks through what happens
from an issue arriving to the deploy finishing.

## 1. An issue is enqueued

An issue id enters a bounded in-memory queue from any of three producers:

- the **Redmine webhook** — a `POST` to `REDMINE_CALLBACK_URI` with body `{"issue_id": <long>}`;
- the **dashboard** — the *issues queue* form (`POST {DASHBOARD_PATH}/issue`);
- the **HTTP API** — `GET /?command=issue&issue_id=<long>`.

## 2. Validation

The redmine-processing thread loads the issue and runs the script at `ISSUE_VALIDATION_SCRIPT`
(JavaScript, executed on the standalone Nashorn engine) with the issue bound as `issue`. The
script must return a boolean:

- returns **false** → the issue is skipped (not a deploy request);
- **throws** → the issue is moved to the *failed* status with the stack trace posted as a comment.

## 3. Parse the deploy request

The first line in the issue **description** that starts with `> deploy` is parsed as an
[alias invocation](/deploy/guides/writing-aliases/#invoking-an-alias). For example:

```
> deploy proc 3.33-40
```

runs the `proc` alias with `$1 = 3.33-40`.

## 4. GitLab diff (optional)

If the alias has an [`diff` block](/deploy/guides/writing-aliases/#example-with-a-diff-block) with
`enabled: true`, the server fetches the GitLab compare between the current version (read from an
agent via `versionUrl`) and the new version (invocation argument `newVersionArg`). It then:

- extracts `#NNNN` Redmine issue references from the commit messages and resolves their subjects;
- **groups the commits by date** (newest day first);
- posts a Redmine comment (a Textile table of `Issue | Subject`, one colspan header row per date);
- sends the same list to Telegram (a plain date header per day, bullets underneath), split into
  multiple messages at ~4000 characters.

## 5. Deploy and status transitions

The server sets the issue status to **Processing** (posting the parsed task as a comment), runs
the alias across its agents (`DeployService.runTask`), and on success sets the status to
**Done**. Any failure sets the status to **Failed**. The status ids are configurable
(`REDMINE_STATUS_ACCEPT_ID`, `…_PROCESSING_ID`, `…_DONE_ID`, `…_FAILED_ID`).

## Durability

Every outbound Redmine mutation and Telegram message is written to a durable disk spool under
`QUEUE_DIR` **before** it is sent, then retried with backoff. Operations that keep failing are
moved to a `dead/` sub-directory (dead-lettering) so they stop being retried but are kept for
inspection. Queue depths and send latency are visible on the dashboard's **Delivery** card and
exported as [metrics](/deploy/reference/metrics/).

## Telegram

Two producers share one rate-limited Telegram queue: the GitLab diff notifications above, and
live task-execution status updates. Telegram is only wired when `TELEGRAM_ENABLED=true`, sending
to `TELEGRAM_CHAT_ID` with `TELEGRAM_TOKEN`.
