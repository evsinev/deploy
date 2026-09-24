---
title: Deploy steps
description: The typed steps a deploy plan is made of, and the policy file each agent host uses to say what a plan may do there.
---

A deploy can be described as a list of **typed steps** instead of a script to run on the agent. The server
builds the plan from an [alias](/deploy/guides/writing-aliases/); the agent checks the whole plan against its
own policy file and then carries it out inside its own process.

Two things follow from that. A plan cannot do anything the host has not already agreed to, and the plan can be
read before it is run — see [`?command=plan`](/deploy/reference/http-api/).

## Steps

Every step is a mapping with a `type` and its parameters. Steps run in the order they are written; the first
failure stops the plan, and nothing is undone — the same contract a sequential script had.

### `fetch`

Downloads an artifact to a file.

| Parameter | Default | Meaning |
|---|---|---|
| `url` | required | Artifact URL; the host must be allowed by the policy. |
| `to` | required | Absolute path of the file to write. |
| `expectStatus` | `200` | The only status treated as success. |
| `mkdirs` | `true` | Create the parent directory if it is missing. |
| `atomic` | `true` | Write beside the destination and move into place. |
| `timeoutSeconds` | `300` | Covers the whole transfer, not just the response headers. |

Redirects are never followed, so a download cannot be steered to another host. An unexpected status stops the
plan and writes nothing — unlike a plain `curl` without `-f`, which stores the error page under the name of the
artifact and lets the next step install it.

### `unpack`

Unpacks a zip archive into a directory.

| Parameter | Default | Meaning |
|---|---|---|
| `archive` | required | Path of the archive. |
| `to` | required | Destination directory. |
| `mode` | `merge` | `merge` writes over what is there, `replace` empties the directory first, `atomic-replace` builds the new contents beside it and swaps them in. |

An entry that would land outside the destination is refused, including one that would get there by following a
symbolic link already sitting in the destination. An archive with no entries — an error page saved as a `.zip`,
for instance — is refused before a replacing mode deletes anything.

### `write-file`

Writes a short text file, typically the version marker a service reads when it starts.

| Parameter | Default | Meaning |
|---|---|---|
| `path` | required | Absolute path of the file. |
| `content` | `""` | Text to write. |
| `newline` | `true` | Append a newline, as a shell redirect does. Set `false` for the equivalent of `echo -n`. |
| `mkdirs` | `false` | Create the parent directory if it is missing. |
| `atomic` | `true` | Write beside the destination and move into place. |

### `copy-file`

Copies the contents of one file over another. The permissions of an existing destination are kept, so a copy
never widens who can read a file.

| Parameter | Default | Meaning |
|---|---|---|
| `from` | required | Source path; must be inside the readable roots. |
| `to` | required | Destination path. |
| `atomic` | `true` | Write beside the destination and move into place. |

### `signal-service`

Asks the process supervisor to act on a service, which is how a service is told to pick up a new version.

| Parameter | Default | Meaning |
|---|---|---|
| `service` | required | Service directory; must be one the policy names. |
| `signal` | `hup` | `hup` or `term`. |

The command is written straight to the supervisor's control channel — the named pipe at
`<service>/supervise/control`, one letter per command — which is exactly what the `svc` program does, and it is
the supervisor that then signals the service. **Nothing is executed**, so a host watching for unexpected
launches inside the container sees none.

A running supervisor holds the reading end of that pipe open, so the write happens at once. If nothing is
reading it the supervisor is not running, and the step says so instead of waiting; the command is not delivered
later, when the supervisor happens to come back.

Where the supervisor does not work this way, set `serviceControl: program` in the policy and the step runs the
program named by `serviceControlBinary` with a fixed argument list instead — never a shell.

### `sleep`

Waits before the next step.

| Parameter | Default | Meaning |
|---|---|---|
| `seconds` | `0` | How long to wait. Zero does nothing and is logged as skipped. |
| `reason` | – | Why the pause is there; shown in the plan and the log. |

A pause is a step of its own rather than an attribute of its neighbours, because the reason for it belongs next
to it. Zero seconds being allowed is what lets one shared recipe serve services that need a pause and services
that do not.

### `check-version`

Refuses a plan that would move an application to a version older than the one it reports now.

| Parameter | Default | Meaning |
|---|---|---|
| `url` | required | Where the application reports its current version. |
| `version` | required | The version about to be deployed. |
| `timeoutSeconds` | `30` | |

Versions are compared part by part and must have the same shape; `1.2.3` against `1.2.3.4` is an error rather
than a guess.

### `wait-url`

Waits until an application reports the version that was just deployed.

| Parameter | Default | Meaning |
|---|---|---|
| `url` | required | Where the application reports its version. |
| `version` | required | The version to wait for. |
| `timeoutSeconds` | required | How long the application may take. |
| `intervalSeconds` | `1` | How often to ask. |
| `failOnOtherVersion` | `true` | Fail at once when the application answers with a different version. |

An unreachable application is expected while it restarts, so that is retried. An application that answers with
a *different* version is not: it means the restart did not pick up the new version. For a service that keeps
answering while it goes down, set `failOnOtherVersion: false` instead of padding the plan with a `sleep`.

### `resolve-version`

Reads a version from a URL and makes it available to later steps as `${name}`.

| Parameter | Default | Meaning |
|---|---|---|
| `url` | required | Where the wanted version is published. |
| `var` | required | Name the value is bound to. |
| `pattern` | `^[0-9][0-9A-Za-z.\-]*$` | What a valid answer looks like. |
| `timeoutSeconds` | `30` | |

This is the only value a plan discovers while it runs, and it ends up in file names and paths, so the answer
must match the pattern. A reference to a variable no earlier step declares is refused before the plan starts.

## The agent policy

Each agent host states what a plan may do there, in the file named by
[`AGENT_POLICY_FILE`](/deploy/configuration/#agent) (default `./etc/policy.yml`). The agent checks a whole plan
against it before running the first step, and reports every problem at once rather than stopping at the first.

**With no policy file the agent refuses step plans** and runs only the older commands, so a host that has not
stated its limits is never handed new powers by default.

```yaml
# Older commands, while aliases are still being moved over. Set to false once none are left.
allowShell: true
shellAllowedPrefix: ./bin          # a command must resolve to a program inside this directory

fetchHosts:                        # where artifacts may be downloaded from
  - artifacts.internal
statusHosts:                       # where a service may be asked for its version
  - 10.20.0.0/16                   # a host name, host:port, or an IPv4 range

writeRoots:                        # where a plan may write
  - /srv/*/staging/**              # one star stays inside a name, two span names
  - /srv/www                       # no wildcard means the directory and everything below it
readRoots: []                      # defaults to writeRoots

serviceDirs:                       # which services may be signalled
  - /service/*
serviceControl: supervise          # write to the supervisor's control channel; nothing is executed (default)
# serviceControl: program          # or run a program instead, for a supervisor that has no control channel
# serviceControlBinary: /usr/bin/svc

limits:
  maxFetchBytes:    1073741824
  maxUnpackBytes:   4294967296
  maxUnpackEntries: 100000
  maxStepSeconds:   600
  maxSleepSeconds:  120
  maxPlanSeconds:   1800
```

A path is resolved the way the operating system resolves it — walked from the root, following every symbolic
link on the way — and only then compared with the roots. A link planted inside an allowed directory therefore
cannot be used to reach outside it. The fixed leading part of each root is resolved the same way, so a root that
is itself a link still works.

Deleting is treated separately from writing: a plan may replace a directory it owns, but never the top of a
root, so `mode: replace` cannot empty `/srv/www` itself.

<br />

:::note
A range under `statusHosts` is compared with the literal address in the URL and never with a resolved name, so
a name that happens to resolve into an allowed range is still refused.
:::

## What the agent must support

An agent reports what it can run when it connects, and the server refuses to send a plan to one that has not
said it can run plans, with a message naming the agent and its version. Update the agents before the aliases
that use steps.
