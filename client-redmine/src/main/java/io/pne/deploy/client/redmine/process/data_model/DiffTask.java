package io.pne.deploy.client.redmine.process.data_model;

import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class DiffTask {
    private Set<String> ids;
    private Integer gitlabProject;
    private String task;
    private String oldVersion;
    private String newVersion;

    // Deploy-review webhook identity, copied from the alias diff block; not part of DiffKey because
    // they are constant for a given (task, gitlabProject) pair.
    private String project;
    private String app;
    private String instance;

    public DiffTask(String[] ids, Integer gitlabProject, String task, String oldVersion, String newVersion) {
        this(ids, gitlabProject, task, oldVersion, newVersion, null, null, null);
    }

    public DiffTask(String[] ids, Integer gitlabProject, String task, String oldVersion, String newVersion
            , String project, String app, String instance) {
        this.ids = new HashSet<>(Arrays.asList(ids));
        this.gitlabProject = gitlabProject;
        this.task = task;
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
        this.project = project;
        this.app = app;
        this.instance = instance;
    }

    public void addIds(Set<String> newIsd) {
        this.ids.addAll(newIsd);
    }

    public String getIdsString() {
        String thisIds = "";
        if (ids != null) {
            thisIds = ids.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));
        }
        return thisIds;
    }

    @Override
    public String toString() {
        return "DiffTask{ids=[" + getIdsString() + "], gitlabProject=" + gitlabProject + ", task=" + task + ", oldVersion=" + oldVersion + ", newVersion=" + newVersion + "}";
    }
}
