---
title: Writing aliases
description: Define a deployment as a YAML alias — commands, target agents, parameters, and an optional diff block.
---

An **alias** is a named deployment recipe stored as a YAML file. A deploy runs an alias across
one or more agents; the same alias can be invoked from a Redmine issue, the dashboard, or the
`?command=run` HTTP endpoint.

## Where aliases live

Aliases are `*.yml` files in the directory given by `VERTX_ALIASES_DIR` (default `./aliases`).
The file name is the alias name — `aliases/proc.yml` defines the `proc` alias. The server throws
at startup if the directory does not exist, and an invocation of an unknown alias fails with a
message listing the available ones.

## Invoking an alias

An alias is invoked by name followed by space-separated parameters:

```
proc 3.33-40
```

- From a **Redmine issue**: put a line starting with `> deploy` in the issue **description**,
  e.g. `> deploy proc 3.33-40`.
- From the **HTTP API**: `GET /?command=run&alias=proc%203.33-40`.
- From the **dashboard**: enqueue the issue that carries the `> deploy …` line.

The invocation text is validated against `^[A-Za-z0-9\s.\-_,;]+$` — only letters, digits,
whitespace and `. - _ , ;` are allowed. No slashes or shell metacharacters, so keep paths and
options inside the alias file, not in the invocation.

### Parameter substitution

Before the YAML is parsed, tokens are substituted textually:

- `$1`, `$2`, … — the positional parameters after the alias name (`$1` = `3.33-40` above).
- `$ISSUE_ID` — the Redmine issue id that triggered the deploy (`0` when there is none).

## Schema

```yaml
commands:                 # required — a list, executed in order
  - agents: <ids>         #   comma-separated agent ids (e.g. web-01,web-02 or localhost)
    name: <executable>    #   the program/script to run on each agent
    arguments:            #   optional list of arguments
      - <arg>
diff:                     # optional — post a GitLab diff to Redmine/Telegram before deploying
  enabled: <bool>
  versionUrl: <url>       #   agent-reachable URL returning the CURRENT version
  gitlabProjectId: <int>
  agent: <id>            #   which agent fetches the current version
  newVersionArg: <int>   #   1-based index of the invocation argument carrying the NEW version
```

Each entry under `commands` becomes a shell command run on every listed agent (see
[Writing commands](./writing-commands/)). The special agent id `localhost` runs in-process on the
server.

## Example

The repository ships one real alias, `server/src/test/resources/aliases/proc.yml`:

```yaml
commands:
- agents: localhost,localhost
  name: echo
  arguments:
    - arg 1
    - $1

- agents: localhost
  name: echo
  arguments:
    - parameter 2
    - $1
```

## Example with a diff block

When `diff.enabled` is `true`, the server fetches the GitLab compare between the current and new
versions and posts it as a Redmine comment and a Telegram message before running the commands.
The **new** version is read from invocation argument `newVersionArg`; the **old** version is
fetched by `agent` from `versionUrl` (the server is often firewalled off from the targets, so an
agent reads it):

```yaml
commands:
- agents: web-01,web-02
  name: /opt/deploy/install.sh
  arguments:
    - myapp
    - $1                                   # new version, e.g. 3.33-40

diff:
  enabled: true
  versionUrl: http://web-01:8080/version   # agent-reachable URL returning the CURRENT version
  gitlabProjectId: 42
  agent: web-01                            # which agent fetches the current version
  newVersionArg: 1                         # $1 in the invocation is the NEW version
```

:::note
The field is `newVersionArg` (1-based; the alias name is token 0, so the first parameter is
index 1). There is no `versionArg` field.
:::

See the [Redmine workflow](./redmine-workflow/) for how the diff is rendered and how issue
status transitions during a deploy.
