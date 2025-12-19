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

    public DiffTask(String[] ids, Integer gitlabProject, String task, String oldVersion, String newVersion) {
        this.ids = new HashSet<>(Arrays.asList(ids));
        this.gitlabProject = gitlabProject;
        this.task = task;
        this.oldVersion = oldVersion;
        this.newVersion = newVersion;
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
