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

There are two forms, and a file is read in whichever one it is written in.

An alias that **declares the values it expects** under `params:` is parsed first, and the values are then put
into the parsed result. A value can only ever become a value: it cannot add a step, change which agents run, or
alter the shape of the file, whatever it contains. This is the form to write new aliases in.

An alias **without** `params:` keeps the older behaviour: `$1`, `$2`, … and `$ISSUE_ID` are replaced in the text
of the file before it is parsed. Nothing about these files changes, and both forms may appear in the same alias
while it is being moved over.

## Schema

```yaml
params:                   # optional — declaring these turns on the checked form
  - name: <name>          #   referred to as ${name} below
    type: <type>          #   version | int | name | url | path | enum | string (default: version)
    required: <bool>      #   default true
    defaultValue: <text>  #   used when the value is not given
    position: <int>       #   which word of the invocation holds it (default: in order of declaration)
    key: <word>           #   or: the word after this keyword holds it
    description: <text>   #   shown when the value is missing
commands:                 # required — a list, executed in order
  - agents: <ids>         #   comma-separated agent ids (e.g. web-01,web-02 or localhost)
    recipe: <name>        #   a shared list of steps, from VERTX_RECIPES_DIR
    with:                 #   the values that recipe expects
      <name>: <value>
  - agents: <ids>
    steps:                #   or: steps written out here, for a one-off
      - type: <type>      #   see Deploy steps
        params: { … }
  - agents: <ids>
    name: <executable>    #   or: the older form — a program to run on each agent
    arguments:            #   optional list of arguments
      - <arg>
diff:                     # optional — post a GitLab diff to Redmine/Telegram before deploying
  enabled: <bool>
  versionUrl: <url>       #   agent-reachable URL returning the CURRENT version
  gitlabProjectId: <int>
  agent: <id>            #   which agent fetches the current version
  newVersionArg: <int>   #   1-based index of the invocation argument carrying the NEW version
  project: <path>        #   optional — GitLab project path for the deploy review webhook
  app: <name>            #   optional — application name (defaults to the alias name)
  instance: <name>       #   optional — target instance name
```

Each command says exactly one of `recipe`, `steps` or `name`. The first two build a plan of
[typed steps](/deploy/reference/deploy-steps/) that the agent carries out itself; `name` is the older form and
runs a program on the agent (see [Writing commands](/deploy/guides/writing-commands/)). The special agent id
`localhost` runs in-process on the server.

## Recipes

A recipe is a shared list of steps with the values it expects, kept in `VERTX_RECIPES_DIR` (default
`./recipes`). It holds what every deployment of a kind does; the alias holds which application, which host and
which version. That separation is what stops the same sequence from being copied once per application and
drifting apart afterwards.

```yaml
# recipes/service-redeploy.yml
description: Download an artifact, record the version, restart the service, wait for it to come back.

params:
  app:         { type: name,    required: true }
  artifact:    { type: name,    required: true }
  version:     { type: version, required: true }
  versionUrl:  { type: url,     required: true }
  waitSeconds: { type: int,     required: true }
  source:      { type: name,    defaultValue: "${site.artifactSource}" }
  stagingDir:  { type: path,    defaultValue: "/srv/${app}/staging" }
  service:     { type: path,    defaultValue: "/service/${app}" }

steps:
  - type: check-version
    params: { url: "${versionUrl}", version: "${version}" }
  - type: fetch
    params:
      url: "http://${source}/artifacts/?artifact=${artifact}&version=${version}"
      to:  "${stagingDir}/${version}"
  - type: write-file
    params: { path: "${stagingDir}/.VERSION", content: "${version}" }
  - type: signal-service
    params: { service: "${service}" }
  - type: wait-url
    params: { url: "${versionUrl}", version: "${version}", timeoutSeconds: "${waitSeconds}" }
```

A default may use the values declared above it. Every value handed to a recipe is held to the type the recipe
declared, whether it came from the invocation, from another value, or from a default.

An alias then names the recipe and fills it in:

```yaml
params:
  - { name: version, type: version, description: version to deploy }

commands:
  - agents: web-01
    recipe: service-redeploy
    with:
      app: myapp
      artifact: myapp-dist
      version: "${version}"
      versionUrl: http://web-01:8080/version
      waitSeconds: 180
```

### Values that differ between sites

`aliases/_site.yml` holds the values that describe where the aliases are deployed rather than what they do, and
they are referred to as `${site.<name>}`. Files whose name starts with `_` are settings, not aliases.

```yaml
# aliases/_site.yml
vars:
  artifactSource: artifacts.internal
```

### Naming values on the invocation

By default the declared values are taken from the words after the alias name, in the order they are declared.
`position` claims a particular word, and `key` takes the word after a keyword — which is what keeps an
invocation carrying several versions readable:

```yaml
params:
  - { name: appVersion,   type: version, key: app }
  - { name: assetVersion, type: version, key: assets }
```

```
release app 3.33-40 assets 1.4-7
```

A missing value is reported with a usage line built from the declaration, a value of the wrong shape is
refused, and a word nothing claims is reported rather than ignored.

### Seeing the plan before running it

`GET /?command=plan&alias=<alias …>` prints what an alias would do, with every value filled in, without sending
anything to an agent. Read it next to the script it replaces while moving a deployment over.

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

See the [Redmine workflow](/deploy/guides/redmine-workflow/) for how the diff is rendered and how issue
status transitions during a deploy.

## Deploy review webhook

`project`, `app` and `instance` are only used by the
[deploy review webhook](/deploy/configuration/#deploy-review-webhook), which fires at deploy start
(the same moment as the 🛫 Telegram message). They name the deployed thing for the receiving
service, since `gitlabProjectId` is a numeric id and the codebase has no other notion of an
instance:

```yaml
diff:
  enabled: true
  versionUrl: http://ams2-app:8080/version
  gitlabProjectId: 114
  agent: ams2-app
  newVersionArg: 1
  project: payneteasy/paynet     # GitLab project path
  app: ams2-paynet-proc          # optional; defaults to the alias name
  instance: AMS-2                # target instance
```

The webhook reuses the versions the `diff:` block already resolves, so it is sent only when
`diff.enabled` is `true` and both `project` and `instance` are set — otherwise the deploy runs
exactly as before and the notification is skipped with a log line. `app` falls back to the alias
name when omitted.
