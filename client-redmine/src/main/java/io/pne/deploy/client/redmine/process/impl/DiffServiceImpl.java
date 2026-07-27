package io.pne.deploy.client.redmine.process.impl;

import io.pne.deploy.client.redmine.process.DiffService;
import io.pne.deploy.client.redmine.process.data_model.DiffKey;
import io.pne.deploy.client.redmine.process.data_model.DiffLink;
import io.pne.deploy.client.redmine.process.data_model.DiffTask;
import io.pne.deploy.client.redmine.remote.IRemoteGitlabService;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import io.pne.deploy.client.redmine.remote.IRemoteTelegramService;
import io.pne.deploy.client.redmine.remote.data_model.DiffCommit;
import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.client.redmine.remote.impl.RemoteGitlabServiceImpl;
import io.pne.deploy.client.redmine.remote.impl.RemoteTelegramServiceImpl;
import io.pne.deploy.server.api.IAgentVersionReader;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiffServiceImpl implements DiffService {
    private static final Logger LOG = LoggerFactory.getLogger(DiffServiceImpl.class);
    private static final Pattern REDMINE_ISSUE_LIKE = Pattern.compile("#(\\d+)");
    private static final int TG_SAFE_MAX = 4000;

    private final IRemoteRedmineService redmine;
    private final IRemoteGitlabService gitlab;
    private final IRemoteTelegramService telegram;
    private final String redmineUrl;
    private final IAgentVersionReader versionReader;

    public DiffServiceImpl(IRemoteRedmineService aRedmine, IRedmineRemoteConfig aConfig, IAgentVersionReader aVersionReader) {
        this(aRedmine, new RemoteGitlabServiceImpl(aConfig), new RemoteTelegramServiceImpl(aConfig), aConfig.url(), aVersionReader);
    }

    public DiffServiceImpl(IRemoteRedmineService aRedmine, IRemoteTelegramService aTelegram, IRedmineRemoteConfig aConfig, IAgentVersionReader aVersionReader) {
        this(aRedmine, new RemoteGitlabServiceImpl(aConfig), aTelegram, aConfig.url(), aVersionReader);
    }

    public DiffServiceImpl(IRemoteRedmineService aRedmine, IRemoteGitlabService aGitlab,
                           IRemoteTelegramService aTelegram, String aRedmineUrl, IAgentVersionReader aVersionReader) {
        this.redmine = aRedmine;
        this.gitlab = aGitlab;
        this.telegram = aTelegram;
        this.redmineUrl = aRedmineUrl;
        this.versionReader = aVersionReader;
    }

    public void processDiff(List<DiffTask> tasks, int issueId) {
        if (tasks == null || tasks.isEmpty()) {
            LOG.info("Diff: nothing to send for issue {} (no diff tasks)", issueId);
            return;
        }

        Map<DiffKey, DiffTask> aggregated = aggregate(tasks);
        LOG.info("Diff: processing {} diff task(s) ({} after aggregation) for issue {}",
                tasks.size(), aggregated.size(), issueId);
        Map<Integer, String> subjectCache = new HashMap<>();

        StringBuilder fullRedmineMessage = new StringBuilder();
        List<String> fullTelegramMessage = new ArrayList<>();

        for (DiffTask diffTask : aggregated.values()) {
            List<DiffCommit> diffs = gitlab.getTagDiff(diffTask);
            LOG.info("Diff: {} commit(s) for {} ({} -> {}), gitlab project {}",
                    diffs == null ? 0 : diffs.size(), diffTask.getIdsString(),
                    diffTask.getOldVersion(), diffTask.getNewVersion(), diffTask.getGitlabProject());
            List<DiffLink> diffLinks = mapDiffIssues(diffs, subjectCache);
            // Newest change first; both the Redmine table and the Telegram list consume this same order.
            diffLinks.sort(Comparator.comparing(DiffLink::getCommitDate,
                    Comparator.nullsLast(Comparator.reverseOrder())));

            fullRedmineMessage.append(constructRedmineMessage(diffTask, diffLinks)).append("\n");
            fullTelegramMessage.addAll(constructTelegramMessage(diffTask, diffLinks));
        }

        LOG.info("Diff: sending Redmine comment ({} chars) and {} Telegram message(s) for issue {}",
                fullRedmineMessage.length(), fullTelegramMessage.size(), issueId);
        redmine.enqueueAddComment(issueId, fullRedmineMessage.toString());
        telegram.sendMessages(fullTelegramMessage);
    }

    public List<DiffTask> getCurrentVersion(Task task) {
        if (task == null || task.diff == null || !task.diff.isEnabled()) {
            LOG.info("Diff: no enabled diff config for '{}'", task == null ? null : task.taskLine);
            return new ArrayList<>();
        }
        TaskDiff diff = task.diff;
        String newVersion = taskVersion(task.taskLine, diff.getNewVersionArg());
        if (newVersion == null) {
            LOG.info("Diff: skip '{}' — no version in the task line", task.taskLine);
            return new ArrayList<>();
        }
        String oldVersion = versionReader.readVersion(diff.getAgent(), diff.getVersionUrl());
        if (oldVersion == null || oldVersion.isEmpty()) {
            LOG.info("Diff: skip '{}' — no old version via agent {} from {}", task.taskLine, diff.getAgent(), diff.getVersionUrl());
            return new ArrayList<>();
        }
        String[] agents = diff.getAgent() == null || diff.getAgent().isBlank()
                ? new String[0] : new String[]{diff.getAgent()};
        LOG.info("Diff: '{}' — gitlabProjectId={}, {} -> {}, agent={}",
                task.taskLine, diff.getGitlabProjectId(), oldVersion, newVersion, diff.getAgent());
        List<DiffTask> diffTasks = new ArrayList<>();
        diffTasks.add(new DiffTask(agents, diff.getGitlabProjectId(), task.taskLine, oldVersion, newVersion));
        return diffTasks;
    }

    /**
     * The new version is the {@code argIndex}-th task-line argument (1-based; {@code tokens[0]} is the alias name,
     * so argument N is {@code tokens[N]}). {@code argIndex == 0} means the diff config has no {@code newVersionArg}.
     */
    private static String taskVersion(String taskLine, int argIndex) {
        if (argIndex <= 0) {
            LOG.error("Diff: skip '{}' — newVersionArg is not set (0)", taskLine);
            return null;
        }
        if (taskLine == null) {
            return null;
        }
        String[] tokens = taskLine.trim().split("\\s+");
        if (tokens.length <= argIndex) {
            LOG.error("Diff: skip '{}' — no argument #{} in the task line", taskLine, argIndex);
            return null;
        }
        return tokens[argIndex];
    }

    Map<DiffKey, DiffTask> aggregate(List<DiffTask> tasks) {
        Map<DiffKey, DiffTask> aggregated = new LinkedHashMap<>();
        if (tasks == null) {
            return aggregated;
        }
        for (DiffTask t : tasks) {
            DiffKey key = new DiffKey(t);
            DiffTask existing = aggregated.get(key);
            if (existing == null) {
                aggregated.put(key, t);
            } else {
                existing.addIds(t.getIds());
            }
        }
        return aggregated;
    }

    List<DiffLink> mapDiffIssues(List<DiffCommit> diffs, Map<Integer, String> subjectCache) {
        List<DiffLink> diffLinks = new ArrayList<>();
        if (diffs == null) return diffLinks;

        for (DiffCommit diff : diffs) {
            String message = diff.getMessage();
            DiffLink diffLink = new DiffLink();
            diffLink.setCommitMessage(message);
            diffLink.setCommitDate(diff.getDate());
            Integer redmineIssueId = parseRedmineIssueIdFromCommitMessage(message);
            diffLink.setRedmineIssueId(redmineIssueId);

            if (redmineIssueId != null) {
                diffLink.setRedmineUrl(redmineUrl + "/issues/" + redmineIssueId);

                String subject = subjectCache.computeIfAbsent(redmineIssueId, id -> {
                    try {
                        return redmine.getIssue(id).subject();
                    } catch (Exception e) {
                        LOG.warn("Can't load Redmine issue subject for {}", id, e);
                        return null;
                    }
                });
                diffLink.setRedmineIssueSubject(subject);
            }
            diffLinks.add(diffLink);
        }
        return diffLinks;
    }


    String constructRedmineMessage(DiffTask task, List<DiffLink> diffLinks) {
        StringBuilder sb = new StringBuilder();
        sb.append("*").append(task.getIdsString()).append("* (").append(task.getOldVersion()).append(" → ").append(task.getNewVersion()).append(")\n\n");
        sb.append("|_. Issue |_. Subject |\n");
        // One colspan-2 header row per date, its commits listed underneath.
        for (Map.Entry<LocalDate, List<DiffLink>> group : groupByDate(dedupByIssue(diffLinks)).entrySet()) {
            sb.append("|\\2. *").append(dateLabel(group.getKey())).append("* |\n");
            for (DiffLink diff : group.getValue()) {
                String issueCell;
                String subject;
                if (diff.getRedmineIssueId() != null) {
                    issueCell = "#" + diff.getRedmineIssueId();
                    subject = diff.getRedmineIssueSubject() != null ? diff.getRedmineIssueSubject().trim() : diff.getCommitMessage();
                } else {
                    issueCell = "";
                    subject = diff.getCommitMessage();
                }
                sb.append("| ").append(textileCell(issueCell))
                        .append(" | ").append(textileCell(subject))
                        .append(" |\n");
            }
        }
        return sb.toString();
    }

    /** Keep the first commit per non-null, non-zero Redmine issue id; all no-issue commits are kept. */
    private static List<DiffLink> dedupByIssue(List<DiffLink> diffLinks) {
        List<DiffLink> result = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (DiffLink diff : diffLinks) {
            Integer issueId = diff.getRedmineIssueId();
            if (issueId != null && issueId != 0 && !seen.add(issueId)) {
                continue;
            }
            result.add(diff);
        }
        return result;
    }

    /**
     * Bucket the commits by date, preserving the caller's (newest-first, nulls-last) order both for the
     * group order and within each group. The {@code null} date key groups undated commits (rendered last).
     */
    private static Map<LocalDate, List<DiffLink>> groupByDate(List<DiffLink> diffLinks) {
        Map<LocalDate, List<DiffLink>> groups = new LinkedHashMap<>();
        for (DiffLink diff : diffLinks) {
            groups.computeIfAbsent(diff.getCommitDate(), k -> new ArrayList<>()).add(diff);
        }
        return groups;
    }

    private static String dateLabel(LocalDate date) {
        return date == null ? "(no date)" : date.toString();
    }

    /** Make text safe for a single Textile table cell: no pipes (they end the cell) and no line breaks (they end the row). */
    private static String textileCell(String s) {
        if (s == null) return "";
        return s.replace("|", "&#124;").replaceAll("\\s*[\\r\\n]+\\s*", " ").trim();
    }

    List<String> constructTelegramMessage(DiffTask task, List<DiffLink> diffLinks) {
        String header = escapeHtml(task.getTask()) +
                " (" + escapeHtml(task.getOldVersion()) +
                " → " + escapeHtml(task.getNewVersion()) +
                ") for " + escapeHtml(task.getIdsString()) +
                "\n\n";
        String changesTitle = "Changes:\n";
        String changesContTitle = "Changes (cont.):\n";
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder(header).append(changesTitle);
        // Commits grouped by date: each date is a plain header line, its bullets listed underneath.
        for (Map.Entry<LocalDate, List<DiffLink>> group : groupByDate(dedupByIssue(diffLinks)).entrySet()) {
            String dateHeader = dateLabel(group.getKey()) + "\n";
            if (current.length() + dateHeader.length() > TG_SAFE_MAX) {
                result.add(current.toString());
                current = new StringBuilder(header).append(changesContTitle);
            }
            current.append(dateHeader);
            for (DiffLink diffLink : group.getValue()) {
                String line = buildTelegramLine(diffLink);

                if (current.length() + line.length() > TG_SAFE_MAX) {
                    result.add(current.toString());
                    // Repeat the date header so bullets are never orphaned from their date after a split.
                    current = new StringBuilder(header).append(changesContTitle).append(dateHeader);
                }
                if (current.length() + line.length() > TG_SAFE_MAX) {
                    int budget = TG_SAFE_MAX - current.length();
                    line = budget > 1 ? line.substring(0, budget - 1) + "\n" : "\n";
                }
                current.append(line);
            }
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    private String buildTelegramLine(DiffLink diffLink) {
        StringBuilder sb = new StringBuilder();
        sb.append("• ");

        Integer issueId = diffLink.getRedmineIssueId();
        if (issueId != null && issueId != 0) {
            sb.append("<a href=\"").append(escapeHtml(diffLink.getRedmineUrl())).append("\">");
            sb.append("#").append(issueId).append(" - ");

            String text = diffLink.getRedmineIssueSubject() != null
                    ? diffLink.getRedmineIssueSubject()
                    : diffLink.getCommitMessage();

            sb.append(escapeHtml(text));
            sb.append("</a>");
        } else {
            sb.append("No Issue - ").append(escapeHtml(diffLink.getCommitMessage()));
        }

        sb.append("\n");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    Integer parseRedmineIssueIdFromCommitMessage(String message) {
        if (message == null) return null;
        Matcher m = REDMINE_ISSUE_LIKE.matcher(message);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

}
