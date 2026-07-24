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
        sb.append("<table><thead><tr><th>queue</th>")
          .append("<th class=\"num\">pending</th><th class=\"num\">dead</th>")
          .append("<th class=\"num\">sent</th><th class=\"num\">dead-lettered</th></tr></thead><tbody>");
        for (Map.Entry<String, PersistentSpool> entry : aQueues.entrySet()) {
            PersistentSpool spool = entry.getValue();
            sb.append("<tr><td>").append(esc(entry.getKey())).append("</td>")
              .append("<td class=\"num\">").append(spool.size()).append("</td>")
              .append("<td class=\"num\">").append(spool.deadSize()).append("</td>")
              .append("<td class=\"num\">").append(spool.sentCount()).append("</td>")
              .append("<td class=\"num\">").append(spool.deadLetterCount()).append("</td></tr>");
        }
        sb.append("</tbody></table>");
        return sb.toString();
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
