package io.pne.deploy.server.vertx.dashboard;

import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;
import io.pne.deploy.server.vertx.status.model.TaskState;
import io.pne.deploy.server.vertx.status.model.TaskStatus;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Pure HTML-fragment rendering for the dashboard. Each method returns the inner HTML for one
 * {@code sse-swap} card; there is no Vert.x dependency here so the rendering is unit-testable.
 */
public final class DashboardView {

    private DashboardView() {
    }

    /** Connected agent ids. */
    public static String agents(Set<String> aAgents) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"kv\"><span>Connected</span><b>").append(aAgents.size()).append("</b></div>");
        if (aAgents.isEmpty()) {
            sb.append("<p class=\"muted\">no agents connected</p>");
        } else {
            sb.append("<ul>");
            for (String agent : aAgents) {
                sb.append("<li><code>").append(esc(agent)).append("</code></li>");
            }
            sb.append("</ul>");
        }
        return sb.toString();
    }

    /** The last task pushed by the execution listener (nullable). */
    public static String status(TaskStatus aStatus) {
        if (aStatus == null) {
            return "<p class=\"muted\">idle &mdash; no task running</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"kv\"><span>State</span>").append(statePill(aStatus.getTaskState())).append("</div>");
        sb.append("<div class=\"kv\"><span>Task id</span><b><code>").append(esc(aStatus.getTaskId())).append("</code></b></div>");
        sb.append("<div class=\"kv\"><span>Issue</span><b>").append(aStatus.getIssueId()).append("</b></div>");
        if (aStatus.getTaskLine() != null) {
            sb.append("<div class=\"kv\"><span>Task</span><b><code>").append(esc(aStatus.getTaskLine())).append("</code></b></div>");
        }
        TaskState state = aStatus.getTaskState();
        if (state != null && state.getErrorMessage() != null) {
            sb.append("<p class=\"pill bad\">").append(esc(state.getErrorMessage())).append("</p>");
        }
        return sb.toString();
    }

    /** Pending Redmine issue ids waiting to be processed. */
    public static String issues(Collection<Long> aIssues) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"kv\"><span>Pending</span><b>").append(aIssues.size()).append("</b></div>");
        if (aIssues.isEmpty()) {
            sb.append("<p class=\"muted\">queue is empty</p>");
        } else {
            sb.append("<ul>");
            for (Long id : aIssues) {
                sb.append("<li><code>#").append(id).append("</code></li>");
            }
            sb.append("</ul>");
        }
        return sb.toString();
    }

    /** Durable delivery queues (telegram, redmine) with their spool counters. */
    public static String queues(Map<String, PersistentSpool> aQueues) {
        if (aQueues.isEmpty()) {
            return "<p class=\"muted\">no queues</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"tablewrap\"><table><thead><tr><th>queue</th>")
          .append("<th class=\"num\">pending</th><th class=\"num\">dead</th>")
          .append("<th class=\"num\">sent</th><th class=\"num\" title=\"dead-lettered\">DLQ</th></tr></thead><tbody>");
        int maxPending = 0;
        for (Map.Entry<String, PersistentSpool> entry : aQueues.entrySet()) {
            PersistentSpool spool = entry.getValue();
            maxPending = Math.max(maxPending, spool.size());
            sb.append("<tr><td>").append(esc(entry.getKey())).append("</td>")
              .append("<td class=\"num\">").append(spool.size()).append("</td>")
              .append("<td class=\"num\">").append(spool.deadSize()).append("</td>")
              .append("<td class=\"num\">").append(spool.sentCount()).append("</td>")
              .append("<td class=\"num\">").append(spool.deadLetterCount()).append("</td></tr>");
        }
        sb.append("</tbody></table></div>");

        // depth bars (pending), scaled to the busiest queue
        sb.append("<div class=\"bars\">");
        for (Map.Entry<String, PersistentSpool> entry : aQueues.entrySet()) {
            int pending = entry.getValue().size();
            double fraction = maxPending == 0 ? 0.0 : (double) pending / maxPending;
            sb.append(barRow(esc(entry.getKey()), Integer.toString(pending), fraction));
        }
        sb.append("</div>");
        return sb.toString();
    }

    /** Per-queue send-latency percentiles as horizontal bars scaled to each queue's max. */
    public static String latency(Map<String, LatencyStat> aStats) {
        if (aStats.isEmpty()) {
            return "<p class=\"muted\">no data</p>";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, LatencyStat> entry : aStats.entrySet()) {
            LatencyStat stat = entry.getValue();
            sb.append("<div class=\"lat\"><div class=\"kv\"><span><b>").append(esc(entry.getKey())).append("</b></span>")
              .append("<span class=\"muted\">n=").append(stat.count())
              .append(", mean ").append(formatMs(stat.meanMs())).append("</span></div>");
            double scale = stat.maxMs() <= 0 ? 1.0 : stat.maxMs();
            sb.append("<div class=\"bars\">");
            sb.append(barRow("p50", formatMs(stat.p50Ms()), stat.p50Ms() / scale));
            sb.append(barRow("p95", formatMs(stat.p95Ms()), stat.p95Ms() / scale));
            sb.append(barRow("p99", formatMs(stat.p99Ms()), stat.p99Ms() / scale));
            sb.append(barRow("max", formatMs(stat.maxMs()), 1.0));
            sb.append("</div></div>");
        }
        return sb.toString();
    }

    /** One horizontal bar: label, a filled track (fraction clamped to [0,1]) and a value caption. */
    static String barRow(String aLabel, String aValueText, double aFraction) {
        long pct = Math.round(Math.max(0.0, Math.min(1.0, aFraction)) * 100);
        return "<div class=\"barrow\">"
                + "<span class=\"barlabel\">" + aLabel + "</span>"
                + "<span class=\"bartrack\"><span class=\"barfill\" style=\"width:" + pct + "%\"></span></span>"
                + "<span class=\"barval\">" + aValueText + "</span>"
                + "</div>";
    }

    static String formatMs(double aMs) {
        if (aMs < 1.0) {
            return String.format("%.1f ms", aMs);
        }
        if (aMs < 1000.0) {
            return Math.round(aMs) + " ms";
        }
        return String.format("%.1f s", aMs / 1000.0);
    }

    /** Coarse JVM/service vitals; values are passed in so the method stays deterministic. */
    public static String service(long aUptimeMs, long aHeapUsed, long aHeapMax, int aAgentCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"kv\"><span>Uptime</span><b>").append(formatDuration(aUptimeMs)).append("</b></div>");
        sb.append("<div class=\"kv\"><span>Heap</span><b>")
          .append(formatBytes(aHeapUsed)).append(" / ").append(formatBytes(aHeapMax)).append("</b></div>");
        sb.append("<div class=\"kv\"><span>Agents</span><b>").append(aAgentCount).append("</b></div>");
        return sb.toString();
    }

    private static String statePill(TaskState aState) {
        if (aState == null || aState.getType() == null) {
            return "<span class=\"pill\">unknown</span>";
        }
        String cls;
        switch (aState.getType()) {
            case SUCCESS: cls = "ok";   break;
            case ERROR:   cls = "bad";  break;
            default:      cls = "warn"; break; // RUNNING
        }
        return "<span class=\"pill " + cls + "\">" + aState.getType() + "</span>";
    }

    static String formatDuration(long aMs) {
        long s = aMs / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600;  s %= 3600;
        long m = s / 60;    s %= 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (d > 0 || h > 0) sb.append(h).append("h ");
        sb.append(m).append("m ").append(s).append("s");
        return sb.toString();
    }

    static String formatBytes(long aBytes) {
        if (aBytes < 1024) {
            return aBytes + " B";
        }
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        double value = aBytes;
        int i = -1;
        do {
            value /= 1024;
            i++;
        } while (value >= 1024 && i < units.length - 1);
        return String.format("%.1f %s", value, units[i]);
    }

    static String esc(String aText) {
        if (aText == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(aText.length());
        for (int i = 0; i < aText.length(); i++) {
            char c = aText.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;");  break;
                case '<':  sb.append("&lt;");   break;
                case '>':  sb.append("&gt;");   break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&#39;");  break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }
}
