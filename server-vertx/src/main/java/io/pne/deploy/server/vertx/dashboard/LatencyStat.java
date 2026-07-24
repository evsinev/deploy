package io.pne.deploy.server.vertx.dashboard;

/**
 * Snapshot of a queue's send-latency timer in milliseconds. Plain numbers (no micrometer types) so
 * {@link DashboardView} stays dependency-free and unit-testable; the handler builds it from the registry.
 */
public record LatencyStat(long count, double meanMs, double p50Ms, double p95Ms, double p99Ms, double maxMs) {
}
