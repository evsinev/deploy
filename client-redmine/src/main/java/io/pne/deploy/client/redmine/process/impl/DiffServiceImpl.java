package io.pne.deploy.client.redmine.process.impl;

import io.pne.deploy.client.redmine.process.DiffService;
import io.pne.deploy.client.redmine.process.data_model.DiffKey;
import io.pne.deploy.client.redmine.process.data_model.DiffLink;
import io.pne.deploy.client.redmine.process.data_model.DiffTask;
import io.pne.deploy.client.redmine.remote.IRemoteGitlabService;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import io.pne.deploy.client.redmine.remote.IRemoteTelegramService;
import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.client.redmine.remote.impl.RemoteGitlabServiceImpl;
import io.pne.deploy.client.redmine.remote.impl.RemoteTelegramServiceImpl;
import io.pne.deploy.server.api.IAgentVersionReader;
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskDiff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            List<String> diffs = gitlab.getTagDiff(diffTask);
            LOG.info("Diff: {} commit(s) for {} ({} -> {}), gitlab project {}",
                    diffs == null ? 0 : diffs.size(), diffTask.getIdsString(),
                    diffTask.getOldVersion(), diffTask.getNewVersion(), diffTask.getGitlabProject());
            List<DiffLink> diffLinks = mapDiffIssues(diffs, subjectCache);

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
        String newVersion = taskVersion(task.taskLine);
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

    /** The deploy version is the first parameter of the task line ("&lt;alias&gt; &lt;version&gt; ..."). */
    private static String taskVersion(String taskLine) {
        if (taskLine == null) {
            return null;
        }
        String[] tokens = taskLine.trim().split("\\s+");
        return tokens.length >= 2 ? tokens[1] : null;
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

    List<DiffLink> mapDiffIssues(List<String> diffs, Map<Integer, String> subjectCache) {
        List<DiffLink> diffLinks = new ArrayList<>();
        if (diffs == null) return diffLinks;

        for (String diff : diffs) {
            DiffLink diffLink = new DiffLink();
            diffLink.setCommitMessage(diff);
            Integer redmineIssueId = parseRedmineIssueIdFromCommitMessage(diff);
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
        List<Integer> addedIssues = new ArrayList<>();
        sb.append("*").append(task.getIdsString()).append("* (").append(task.getOldVersion()).append(" → ").append(task.getNewVersion()).append(")\n\n");
        sb.append("Changes:\n");
        for (DiffLink diff : diffLinks) {
            if (diff.getRedmineIssueId() != null && diff.getRedmineIssueId() != 0 && addedIssues.contains(diff.getRedmineIssueId())) {
                continue;
            } else {
                addedIssues.add(diff.getRedmineIssueId());
            }
            sb.append("# ");
            if (diff.getRedmineIssueId() != null) {
                sb.append("#").append(diff.getRedmineIssueId()).append(" - ");
                if (diff.getRedmineIssueSubject() != null) {
                    sb.append(diff.getRedmineIssueSubject().trim());
                } else {
                    sb.append(diff.getCommitMessage());
                }
            } else {
                sb.append("No Issue - ").append(diff.getCommitMessage());
            }
            sb.append("\n");
        }
        return sb.toString();
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
        Set<Integer> addedIssues = new HashSet<>();
        for (DiffLink diffLink : diffLinks) {
            Integer issueId = diffLink.getRedmineIssueId();
            if (issueId != null && issueId != 0 && !addedIssues.add(issueId)) {
                continue;
            }
            String line = buildTelegramLine(diffLink);

            if (current.length() + line.length() > TG_SAFE_MAX) {
                result.add(current.toString());
                current = new StringBuilder(header).append(changesContTitle);
            }
            if (current.length() + line.length() > TG_SAFE_MAX) {
                int budget = TG_SAFE_MAX - current.length();
                line = budget > 1 ? line.substring(0, budget - 1) + "\n" : "\n";
            }
            current.append(line);
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
