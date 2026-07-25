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
import io.pne.deploy.server.api.task.Task;
import io.pne.deploy.server.api.task.TaskCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiffServiceImpl implements DiffService {
    private static final Logger LOG = LoggerFactory.getLogger(DiffServiceImpl.class);
    private static final Pattern LINK_LIKE = Pattern.compile("^(https?://|http:).+");
    private static final Pattern VERSION_LIKE = Pattern.compile("^\\d+(?:\\.\\d+){2}(?:-\\d+)?$");
    private static final Pattern GITLAB_PROJECT_LIKE = Pattern.compile("^gitlab=(\\d+)$");
    private static final Pattern REDMINE_ISSUE_LIKE = Pattern.compile("#(\\d+)");
    private static final int TG_SAFE_MAX = 4000;

    private final IRemoteRedmineService redmine;
    private final IRemoteGitlabService gitlab;
    private final IRemoteTelegramService telegram;
    private final String redmineUrl;

    public DiffServiceImpl(IRemoteRedmineService aRedmine, IRedmineRemoteConfig aConfig) {
        this(aRedmine, new RemoteGitlabServiceImpl(aConfig), new RemoteTelegramServiceImpl(aConfig), aConfig.url());
    }

    public DiffServiceImpl(IRemoteRedmineService aRedmine, IRemoteTelegramService aTelegram, IRedmineRemoteConfig aConfig) {
        this(aRedmine, new RemoteGitlabServiceImpl(aConfig), aTelegram, aConfig.url());
    }

    public DiffServiceImpl(IRemoteRedmineService aRedmine, IRemoteGitlabService aGitlab,
                           IRemoteTelegramService aTelegram, String aRedmineUrl) {
        this.redmine = aRedmine;
        this.gitlab = aGitlab;
        this.telegram = aTelegram;
        this.redmineUrl = aRedmineUrl;
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
        List<DiffTask> diffTasks = new ArrayList<>();
        if (task == null || task.commands.isEmpty()) {
            LOG.info("Diff: no task/commands, nothing to diff");
            return diffTasks;
        }
        // A command is diff-eligible purely by its arguments: a version, an old-version url, and a gitlab=<id>.
        LOG.info("Diff: scanning {} command(s) of '{}' (need version + url + gitlab=<id>)",
                task.commands.size(), task.taskLine);
        for (TaskCommand command : task.commands) {
            if (command == null) {
                continue;
            }
            String name = command.command.name;
            try {
                String newVersion = parseStringFromArguments(command.command.arguments, VERSION_LIKE);
                if (newVersion == null) {
                    LOG.info("Diff: skip '{}' — no version argument", name);
                    continue;
                }
                String linkForOldVersion = parseStringFromArguments(command.command.arguments, LINK_LIKE);
                if (linkForOldVersion == null) {
                    LOG.info("Diff: skip '{}' — no old-version url argument", name);
                    continue;
                }
                Integer gitlabProject = parseGitlabProjectFromArguments(command.command.arguments);
                if (gitlabProject == null) {
                    LOG.info("Diff: skip '{}' — no gitlab=<id> argument", name);
                    continue;
                }
                String oldVersion = getUrlContent(linkForOldVersion);
                if (oldVersion == null || oldVersion.isEmpty()) {
                    LOG.info("Diff: skip '{}' — old version is empty from {}", name, linkForOldVersion);
                    continue;
                }
                LOG.info("Diff: include '{}' — gitlab={}, {} -> {}, agents={}",
                        name, gitlabProject, oldVersion, newVersion, Arrays.toString(command.agents.getIds()));
                diffTasks.add(new DiffTask(command.agents.getIds(),
                        gitlabProject,
                        task.taskLine,
                        oldVersion,
                        newVersion));
            } catch (Exception e) {
                LOG.error("Diff: can't build diff task for command '{}'", name, e);
            }
        }
        LOG.info("Diff: {} diff task(s) built for '{}'", diffTasks.size(), task.taskLine);
        return diffTasks;
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

    private Integer parseGitlabProjectFromArguments(List<String> arguments) {
        if (arguments == null || arguments.isEmpty()) return null;
        for (String s : arguments) {
            Matcher m = GITLAB_PROJECT_LIKE.matcher(s);
            if (m.matches()) {
                return Integer.parseInt(m.group(1));
            }
        }
        return null;
    }

    private String parseStringFromArguments(List<String> arguments, Pattern pattern) {
        if (arguments == null || arguments.isEmpty()) return null;
        for (String s : arguments) {
            if (pattern.matcher(s).matches()) {
                return s;
            }
        }
        return null;
    }

    private static String getUrlContent(String aUrl) throws IOException {
        LOG.info("Loading {}", aUrl);
        URL url = new URL(aUrl);
        URLConnection con = url.openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(10_000);
        InputStream in = con.getInputStream();
        if (in == null) {
            throw new IllegalStateException("Input stream is null for " + url);
        }
        try {
            Scanner scanner = new Scanner(in, "utf-8");
            if (scanner.hasNextLine()) {
                return scanner.nextLine();
            } else {
                throw new IllegalStateException("No content for url " + url);
            }
        } finally {
            in.close();
        }
    }
}
