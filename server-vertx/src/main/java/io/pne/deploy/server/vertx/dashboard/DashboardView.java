package io.pne.deploy.server.vertx.dashboard;

import io.pne.deploy.client.redmine.remote.queue.PersistentSpool;
import io.pne.deploy.server.service.impl.alias.AliasCommand;
import io.pne.deploy.server.service.impl.alias.AliasDescription;
import io.pne.deploy.server.vertx.status.model.TaskState;
import io.pne.deploy.server.vertx.status.model.TaskStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure HTML-fragment rendering for the dashboard. Each method returns the inner HTML for one
 * {@code sse-swap} card; there is no Vert.x dependency here so the rendering is unit-testable.
 */
public final class DashboardView {

    private DashboardView() {
    }

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** Connected agent ids, rendered as the AGENTS block (label + count pill + chips) of the task card. */
    public static String agents(Set<String> aAgents) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"agents-head\"><span class=\"cmd-label\">agents</span>")
          .append("<span class=\"agents-count\">").append(aAgents.size()).append(" connected</span></div>");
        if (aAgents.isEmpty()) {
            sb.append("<p class=\"muted\">no agents connected</p>");
        } else {
            sb.append("<div class=\"chips\">");
            for (String agent : new java.util.TreeSet<>(aAgents)) { // alphabetical
                sb.append("<span class=\"chip\">").append(esc(agent)).append("</span>");
            }
            sb.append("</div>");
        }
        return sb.toString();
    }

    /** The last task pushed by the execution listener (nullable). {@code aRedmineBaseUrl} links the issue id. */
    public static String status(TaskStatus aStatus, String aRedmineBaseUrl) {
        if (aStatus == null) {
            return "<div class=\"task-head\"><span class=\"cmd-label\">current task</span></div>"
                    + "<p class=\"muted\">idle &mdash; no task running</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"task-head\"><span class=\"cmd-label\">current task</span>")
          .append(stateBadge(aStatus.getTaskState())).append("</div>");
        if (aStatus.getTaskLine() != null) {
            sb.append("<div class=\"task-name\"><code>").append(esc(aStatus.getTaskLine())).append("</code></div>");
        }
        sb.append("<div class=\"task-meta\">")
          .append("<div class=\"meta-col\"><span class=\"cmd-label\">task id</span>")
          .append("<code class=\"meta-val\">").append(esc(aStatus.getTaskId())).append("</code></div>")
          .append("<div class=\"meta-col\"><span class=\"cmd-label\">issue</span>")
          .append(issueLink(aStatus.getIssueId(), aRedmineBaseUrl)).append("</div>")
          .append("</div>");
        TaskState state = aStatus.getTaskState();
        if (state != null && state.getErrorMessage() != null) {
            sb.append("<p class=\"task-err\">").append(esc(state.getErrorMessage())).append("</p>");
        }
        return sb.toString();
    }

    private static String issueLink(long aIssueId, String aRedmineBaseUrl) {
        String label = "#" + aIssueId;
        if (aRedmineBaseUrl == null || aRedmineBaseUrl.isBlank()) {
            return "<code class=\"meta-val\">" + label + "</code>";
        }
        String base = aRedmineBaseUrl.endsWith("/")
                ? aRedmineBaseUrl.substring(0, aRedmineBaseUrl.length() - 1) : aRedmineBaseUrl;
        return "<a class=\"meta-val issue-link\" href=\"" + esc(base) + "/issues/" + aIssueId
                + "\" target=\"_blank\" rel=\"noopener\">" + label + "</a>";
    }

    /** Pending Redmine issue ids waiting to be processed. */
    public static String issues(Collection<Long> aIssues) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"issues-count\">pending <b>").append(aIssues.size()).append("</b></div>");
        if (aIssues.isEmpty()) {
            sb.append("<div class=\"issues-empty\">queue is empty</div>");
        } else {
            sb.append("<div class=\"issues-list\">");
            for (Long id : aIssues) {
                sb.append("<div class=\"issue-row\"><code>#").append(id).append("</code></div>");
            }
            sb.append("</div>");
        }
        return sb.toString();
    }

    /** Delivery queues + send-latency merged into one table: one row per queue, all values as numbers. */
    public static String delivery(Map<String, PersistentSpool> aQueues, Map<String, LatencyStat> aLatency) {
        if (aQueues.isEmpty()) {
            return "<p class=\"muted\">no queues</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"delivery-wrap\"><table class=\"delivery\"><thead><tr>")
          .append("<th class=\"l\">queue</th>")
          .append("<th>pending</th><th>dead</th><th>sent</th><th>dlq</th>")
          .append("<th class=\"n\">n</th><th>mean</th><th>p50</th><th>p95</th><th>p99</th><th>max</th>")
          .append("</tr></thead><tbody>");
        for (Map.Entry<String, PersistentSpool> entry : aQueues.entrySet()) {
            PersistentSpool spool = entry.getValue();
            int  pending = spool.size();
            long dead    = spool.deadSize();
            long dlq     = spool.deadLetterCount();
            sb.append("<tr><td class=\"l\">").append(esc(entry.getKey())).append("</td>")
              .append(numCell(pending, pending > 0 ? "warn" : null))
              .append(numCell(dead, dead > 0 ? "bad" : null))
              .append(numCell(spool.sentCount(), null))
              .append(numCell(dlq, dlq > 0 ? "bad" : null));
            LatencyStat stat = aLatency == null ? null : aLatency.get(entry.getKey());
            if (stat == null || stat.count() == 0) {
                sb.append("<td class=\"n\">&mdash;</td><td>&mdash;</td><td>&mdash;</td>")
                  .append("<td>&mdash;</td><td>&mdash;</td><td>&mdash;</td>");
            } else {
                sb.append("<td class=\"n\">").append(stat.count()).append("</td>")
                  .append("<td>").append(formatMs(stat.meanMs())).append("</td>")
                  .append("<td>").append(formatMs(stat.p50Ms())).append("</td>")
                  .append("<td>").append(formatMs(stat.p95Ms())).append("</td>")
                  .append("<td>").append(formatMs(stat.p99Ms())).append("</td>")
                  .append("<td>").append(formatMs(stat.maxMs())).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        return sb.toString();
    }

    private static String numCell(long aValue, String aClass) {
        return aClass == null ? "<td>" + aValue + "</td>" : "<td class=\"" + aClass + "\">" + aValue + "</td>";
    }

    /** Recent agent command-output log lines (newest first). Connect/disconnect are not shown. */
    public static String logs(List<AgentLogBuffer.LogLine> aLines) {
        if (aLines.isEmpty()) {
            return "<p class=\"muted\">no logs yet</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"logbox\">");
        for (AgentLogBuffer.LogLine line : aLines) {
            String time = LOG_TIME.format(Instant.ofEpochMilli(line.epochMs()).atZone(ZoneId.systemDefault()));
            sb.append("<div class=\"logline\"><span class=\"log-time\">").append(time).append("</span>")
              .append("<code class=\"log-id\">#").append(esc(shortId(line.commandId()))).append("</code>")
              .append("<span class=\"log-msg\">").append(esc(line.message())).append("</span>")
              .append("</div>");
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String shortId(String aCommandId) {
        if (aCommandId == null) {
            return "?";
        }
        return aCommandId.length() > 8 ? aCommandId.substring(0, 8) : aCommandId;
    }

    /**
     * Full Config screen: a filter toolbar plus one card per group with a filterable grid of rows. Secrets are
     * already masked in the entries and never carry the real value. The client filters the rendered rows.
     */
    public static String config(List<StartupConfigReport.Entry> aEntries) {
        if (aEntries.isEmpty()) {
            return "<p class=\"muted\">no config</p>";
        }
        int overridden = 0;
        for (StartupConfigReport.Entry e : aEntries) {
            if (isOverridden(e)) {
                overridden++;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"cfg-toolbar\">")
          .append("<input class=\"cfg-filter\" type=\"text\" placeholder=\"filter variables&hellip;\" autocomplete=\"off\" spellcheck=\"false\">")
          .append("<button class=\"cfg-toggle\" type=\"button\">overridden only</button>")
          .append("<span class=\"cfg-summary\">").append(aEntries.size()).append(" variables &middot; ")
          .append(overridden).append(" overridden</span></div>");

        Map<String, List<StartupConfigReport.Entry>> byGroup = new java.util.LinkedHashMap<>();
        for (StartupConfigReport.Entry e : aEntries) {
            byGroup.computeIfAbsent(e.group(), k -> new java.util.ArrayList<>()).add(e);
        }
        for (Map.Entry<String, List<StartupConfigReport.Entry>> group : byGroup.entrySet()) {
            List<StartupConfigReport.Entry> rows = group.getValue();
            int groupOvr = 0;
            for (StartupConfigReport.Entry e : rows) {
                if (isOverridden(e)) {
                    groupOvr++;
                }
            }
            sb.append("<section class=\"cfg-card\"><div class=\"cfg-card-head\">")
              .append("<span class=\"cfg-group-label\">").append(esc(group.getKey())).append("</span>")
              .append("<span class=\"cfg-count\" data-total=\"").append(rows.size()).append("\">")
              .append(rows.size()).append(" of ").append(rows.size()).append("</span>");
            if (groupOvr > 0) {
                sb.append("<span class=\"cfg-ovr\">").append(groupOvr).append(" overridden</span>");
            }
            sb.append("</div><div class=\"cfg-grid\">")
              .append("<div class=\"cfg-row cfg-th\"><span>variable</span><span>value</span><span>default</span></div>");
            for (StartupConfigReport.Entry e : rows) {
                boolean ovr   = isOverridden(e);
                String  value = e.masked() ? "••••" : e.value();
                String  def   = e.def() == null || e.def().isEmpty() ? "—" : e.def();
                sb.append("<div class=\"cfg-row\" data-var=\"").append(esc(e.name().toLowerCase()))
                  .append("\" data-value=\"").append(esc(e.value() == null ? "" : e.value().toLowerCase()))
                  .append("\" data-overridden=\"").append(ovr).append("\">")
                  .append("<span class=\"cfg-var\"><span class=\"cfg-dot").append(ovr ? " on" : "").append("\"></span>")
                  .append("<span class=\"cfg-name\">").append(esc(e.name())).append("</span></span>")
                  .append("<span class=\"cfg-value\"><span class=\"v-").append(valueKind(e)).append("\">")
                  .append(esc(value)).append("</span>");
                if (e.masked() && isSecretSet(e)) {
                    sb.append("<span class=\"cfg-set\">set</span>");
                }
                sb.append("</span><span class=\"cfg-def\">").append(esc(def)).append("</span></div>");
            }
            sb.append("</div></section>");
        }
        sb.append("<div class=\"cfg-empty\" hidden>nothing matches the current filter</div>");
        return sb.toString();
    }

    private static boolean isOverridden(StartupConfigReport.Entry aEntry) {
        return aEntry.masked() ? isSecretSet(aEntry) : !aEntry.isDefault();
    }

    private static boolean isSecretSet(StartupConfigReport.Entry aEntry) {
        return aEntry.value() != null && aEntry.value().contains("(set)");
    }

    /** Coarse value classification for colour-coding: secret / num / bool / path / str. */
    private static String valueKind(StartupConfigReport.Entry aEntry) {
        if (aEntry.masked()) {
            return "secret";
        }
        String v = aEntry.value() == null ? "" : aEntry.value().trim();
        if (v.matches("-?\\d+")) {
            return "num";
        }
        if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
            return "bool";
        }
        if (v.startsWith("/") || v.startsWith("./") || v.startsWith("../")) {
            return "path";
        }
        return "str";
    }

    /** One alias in the sidebar list: name + number of commands. */
    public record AliasInfo(String name, int commandCount) {
    }

    /** Aliases sidebar: header (title + count) + client-side filter input + clickable list. */
    public static String aliasSidebar(List<AliasInfo> aAliases, String aBasePath) {
        StringBuilder sb = new StringBuilder();
        sb.append("<aside class=\"alias-side\">");
        sb.append("<div class=\"alias-side-head\"><div class=\"alias-side-row\">")
          .append("<span class=\"alias-side-title\">Aliases</span>")
          .append("<span class=\"alias-count\">").append(aAliases.size()).append("</span></div>")
          .append("<input class=\"alias-filter\" type=\"text\" placeholder=\"filter&hellip;\" autocomplete=\"off\" spellcheck=\"false\"></div>");
        sb.append("<div class=\"alias-list\">");
        if (aAliases.isEmpty()) {
            sb.append("<div class=\"muted\" style=\"padding:8px 10px\">no aliases</div>");
        } else {
            for (AliasInfo alias : aAliases) {
                String meta = alias.commandCount() == 1 ? "1 command" : alias.commandCount() + " commands";
                sb.append("<button class=\"alias-item\" hx-get=\"").append(esc(aBasePath)).append("/alias?name=").append(esc(alias.name()))
                  .append("\" hx-target=\"#alias-detail\" hx-swap=\"innerHTML\">")
                  .append("<span class=\"alias-name\">").append(esc(alias.name())).append("</span>")
                  .append("<span class=\"alias-sub\">").append(meta).append("</span></button>");
            }
            sb.append("<div class=\"alias-nomatch\">no matches</div>");
        }
        sb.append("</div></aside>");
        return sb.toString();
    }

    /** Rendered alias definition: per-command agents/name/arguments cards + a toggleable raw-YAML block. */
    public static String aliasDetail(String aName, AliasDescription aDescription, String aRawYaml) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"alias-head\"><h2 class=\"alias-title\">").append(esc(aName)).append("</h2>");
        if (aRawYaml != null) {
            sb.append("<button class=\"alias-rawbtn\" onclick=\"var r=document.getElementById('alias-raw');")
              .append("r.hidden=!r.hidden;this.textContent=r.hidden?'show yaml':'hide yaml';\">show yaml</button>");
        }
        sb.append("</div>");

        if (aDescription == null || aDescription.commands == null || aDescription.commands.isEmpty()) {
            sb.append("<p class=\"muted\">no commands / could not parse</p>");
        } else {
            int index = 1;
            for (AliasCommand command : aDescription.commands) {
                sb.append("<section class=\"cmd-card\"><div class=\"cmd-head\">")
                  .append("<span class=\"cmd-num\">").append(index++).append("</span>")
                  .append("<span class=\"cmd-label\">agents</span><span class=\"chips\">");
                if (command.agents != null) {
                    for (String agent : command.agents.split(",")) {
                        sb.append("<span class=\"chip\">").append(esc(agent.trim())).append("</span>");
                    }
                }
                sb.append("</span></div><div class=\"cmd-body\">");
                sb.append("<span class=\"cmd-label\">name</span><code class=\"code\">").append(esc(command.name)).append("</code>");
                if (command.arguments != null && !command.arguments.isEmpty()) {
                    sb.append("<span class=\"cmd-label\">arguments</span><div class=\"args\">");
                    int k = 1;
                    for (String arg : command.arguments) {
                        sb.append("<div class=\"arg\"><span class=\"arg-i\">").append(k++).append("</span>")
                          .append("<code class=\"code\">").append(esc(arg)).append("</code></div>");
                    }
                    sb.append("</div>");
                }
                sb.append("</div></section>");
            }
        }

        if (aRawYaml != null) {
            sb.append("<div id=\"alias-raw\" class=\"raw-card\" hidden><pre>").append(esc(aRawYaml)).append("</pre></div>");
        }
        return sb.toString();
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

    /** Coarse JVM/service vitals rendered as inline header metrics (uptime / heap / agents). */
    public static String service(long aUptimeMs, long aHeapUsed, long aHeapMax, int aAgentCount) {
        return "<span class=\"vital\">uptime <b>" + formatDuration(aUptimeMs) + "</b></span>"
                + "<span class=\"vital\">heap <b>" + formatBytes(aHeapUsed) + " / " + formatBytes(aHeapMax) + "</b></span>"
                + "<span class=\"vital\">agents <b>" + aAgentCount + "</b></span>";
    }

    private static String stateBadge(TaskState aState) {
        if (aState == null || aState.getType() == null) {
            return "<span class=\"badge\">unknown</span>";
        }
        String cls;
        switch (aState.getType()) {
            case SUCCESS: cls = "ok";   break;
            case ERROR:   cls = "bad";  break;
            default:      cls = "warn"; break; // RUNNING
        }
        return "<span class=\"badge " + cls + "\">" + aState.getType() + "</span>";
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
