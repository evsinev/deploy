---
title: Writing commands
description: What a command is, how the agent runs it, and the bundled helper CLIs.
---

A **command** is a single entry under an alias's `commands:` list. Each command names an
executable and its arguments, and the server dispatches it to every agent listed in `agents`.

## How a command runs

On the agent, a command is executed as an ordinary operating-system process: the agent runs
`name` with `arguments` via `ProcessBuilder`, streams the combined stdout/stderr back to the
server as log lines (visible on the dashboard's **Agent logs** screen), and treats a **non-zero
exit code as a failure** that fails the whole task.

Key points:

- The only command type is `SHELL` — there is no built-in DSL. "Writing a command" means naming
  any executable or script that already exists on the agent host, plus its arguments.
- The agent id `localhost` runs the command **in-process on the server** instead of on a remote
  agent — useful for local commands and for testing an alias without a real agent.
- If `name` ends in `.sh`, exists, and is not executable, the agent adds the owner execute bit
  before running it, so you don't have to `chmod +x` deployed scripts.
- Arguments are passed verbatim as separate process arguments; there is no shell in between, so
  quoting, globs, and pipes are **not** interpreted. To use shell features, call a shell
  explicitly (`name: /bin/sh`, `arguments: ["-c", "…"]`) or put them inside a `.sh` script.

```yaml
commands:
- agents: web-01,web-02
  name: /opt/deploy/install.sh
  arguments:
    - myapp
    - $1
```

## Bundled helper commands

The `commands` module builds a small helper CLI (dispatched by `RunnerMain`) that you can deploy
to agent hosts and invoke as the `name`/`arguments` of an alias command. The first argument
selects the command.

### CheckVersion

```
CheckVersion <versionUrl> <newVersion>
```

Fetches the current version from `versionUrl` and compares it to `newVersion` numerically
(tokens split on `.`, `-`, `_`, `;`, `,`, or space). Exits non-zero — "New version must be
greater or equals" — if `newVersion` is lower than the current one. Use it as a guard before an
upgrade so you never deploy an older build.

### WaitUrl

```
WaitUrl <versionUrl> <expectedContent> <secondsToWait>
```

Polls `versionUrl` until its first line equals `expectedContent` (exit 0) or the timeout elapses
(exit non-zero). Use it after a restart to wait until the service reports the version you just
deployed.

```yaml
commands:
- agents: web-01
  name: java
  arguments:
    - -jar
    - /opt/deploy/commands.jar
    - WaitUrl
    - http://web-01:8080/version
    - "3.33-40"
    - "60"
```

:::note
These snippets are authored from the CLI signatures — the repository ships no alias that calls
the helpers, so adapt the jar path and arguments to your hosts.
:::

## Related: the diff version reader

The optional [alias `diff` block](./writing-aliases/#example-with-a-diff-block) reads the current
version through an agent from an HTTP `versionUrl` (the same idea as `WaitUrl`/`CheckVersion`,
but performed server-side to build the GitLab compare). Expose a plain-text version endpoint on
each service and point `versionUrl` at it.
