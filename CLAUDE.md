# CLAUDE.md

Guidance for Claude Code (and other agents) working in this repository. See `README.md` for the
user-facing overview; this file is about building, testing, and the conventions/gotchas that are
easy to get wrong.

## Build & test

- **JDK 21 is required** (Maven compiler `source`/`target` = 21). Building on an older JDK fails.
- Full build + tests (the CI gate):
  ```bash
  mvn -B -ntp verify        # or ./mvnw -B -ntp verify (wrapper pins Maven 3.9.4)
  ```
- Run one test across the reactor:
  ```bash
  mvn -B -ntp test -Dtest=ClassName#method -Dsurefire.failIfNoSpecifiedTests=false
  ```
  The `-Dsurefire.failIfNoSpecifiedTests=false` flag is mandatory with `-Dtest=` because most
  modules don't contain the named test and would otherwise fail the run.
- Test stack: **JUnit 4** (`junit:4.13.2`), **Mockito 5** (`mockito-core:5.14.2`), and
  `vertx-unit`. Do not assume JUnit 5 APIs.

## CI

`.github/workflows/ci.yml` — Temurin **JDK 21**, `mvn -B -ntp verify`, on push to `master` and on
all pull requests. Always get this green before pushing. There is no Travis or other CI.

## Modules (reactor order)

`util` (config marker) · `commands` (agent CLI helpers) · `agent-api` (wire contract) ·
`server-api` (deploy domain) · `agent` (shell executor) · `server` (deploy core + alias parsing) ·
`client-redmine` (Redmine/GitLab/Telegram + durable queues) · `server-vertx` (Vert.x runtime +
entry point) · `agent-websocket` (agent entry point) · `integration-test` (end-to-end).

groupId is `io.pne.deploy`; parent is `io.pne:deploy:1.0-SNAPSHOT`.

## Conventions

- **Logging**: manual `private static final Logger LOG = LoggerFactory.getLogger(X.class);` —
  Lombok `@Slf4j` is **not** used.
- **Lombok**: `@Data` / `@Builder` / `@Value` on model/data classes (`provided` scope; `client-redmine`
  overrides it to `compile`).
- **Immutables**: `@Value.Immutable` interfaces generate `Immutable*` builders
  (`ImmutableRedmineIssue.builder()…`). Only `client-redmine` declares the dependency.
- **Env config**: config interfaces `extends io.pne.deploy.util.env.IStartupConfig` with methods
  annotated `@AStartupParameter(name = "ENV_VAR", value = "default"[, maskVariable = true])`
  (`value` is always a String literal, even for `int`/`long`/`boolean`). Resolve at runtime with
  `StartupParametersFactory.getStartupParameters(SomeConfig.class)`. Each interface is independent.
  The three interfaces: `IRedmineRemoteConfig`, `IVertxServerConfiguration`, `IDashboardConfig`.
  The agent is the exception — plain `IAgentStartupParameters` read via `System.getenv`
  (`SERVER_BASE_URL`, `AGENT_ID`, both required).
- **Parameter naming**: constructor/method parameters are prefixed `aXxx`; fields are unprefixed.
  Follow the surrounding style when editing.

## Gotchas (do not regress these)

- **Gson + JDK strong encapsulation.** Messages carry an `Exception` field (e.g.
  `RunAgentCommandResponse.error`); Gson reflects into `java.lang.Throwable`, which JDK 17+ blocks.
  This is handled in two places that must stay in sync:
  - surefire `argLine` in the parent pom: `--add-opens java.base/java.lang=ALL-UNNAMED`;
  - `Add-Opens: java.base/java.lang` manifest entry in **both** fat-jar assemblies
    (`server-vertx/pom.xml`, `agent-websocket/pom.xml`).
  Removing either breaks tests or the runnable jars at runtime.
- **Nashorn.** The JS engine was removed from the JDK, so `ISSUE_VALIDATION_SCRIPT` runs on the
  standalone `org.openjdk.nashorn:nashorn-core:15.4` (`runtime` scope, `client-redmine`).
- **micrometer is pinned to `1.12.6`** so the `io.micrometer.prometheus.*` package still exists
  (`PrometheusConfig`, `PrometheusMeterRegistry`). Micrometer 1.13+ moved these to
  `io.micrometer.prometheusmetrics`; do not bump without updating the imports.
- **The server binds `127.0.0.1` only** (`WebSocketVerticle.start`). Tests hit `http://127.0.0.1:<port>`.

## Runnable artifacts

Both entry points are packaged as `jar-with-dependencies` by `maven-assembly-plugin`:
- Server: main `io.pne.deploy.server.vertx.VertxServerApplication` →
  `server-vertx/target/server-vertx-1.0-SNAPSHOT-jar-with-dependencies.jar`.
- Agent: main `io.pne.deploy.agent.websocket.WebSocketAgentApplication`.

Release: `release-deploy-server.sh` runs `./mvnw clean package` and uploads the server jar to an
internal endpoint. It contains a hardcoded api-key — treat it as sensitive.

## Where things live

- HTTP routing: `server-vertx/src/main/java/io/pne/deploy/server/vertx/http/HttpHandler.java`
  (order: Redmine callback → dashboard → status → `/metrics` → `?command=`).
- Server wiring / `main`: `server-vertx/.../VertxServerApplication.java`,
  `server-vertx/.../WebSocketVerticle.java`.
- Dashboard (htmx + SSE): `server-vertx/.../dashboard/` (`DashboardHttpHandler`, `DashboardView`,
  `IDashboardConfig`, `resources/dashboard/`).
- Metrics: `server-vertx/.../metrics/` (`QueueMetrics`, `MetricsHttpHandler`).
- Durable queues: `client-redmine/.../remote/queue/PersistentSpool.java` (+ `Backoff`).
- Redmine/GitLab/Telegram: `client-redmine/.../process/impl/` and `client-redmine/.../remote/impl/`.

## Don't

- Don't commit secrets. `test.env` and `out.txt` are already tracked with plaintext secrets/noise —
  don't add more, and prefer environment variables for all credentials.
- Don't break `mvn -B -ntp verify` under JDK 21 — it's the CI gate.
