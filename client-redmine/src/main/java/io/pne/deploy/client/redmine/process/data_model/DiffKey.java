package io.pne.deploy.client.redmine.process.data_model;

public class DiffKey {
    private final String task;
    private final Integer gitlabProject;
    private final String oldVersion;
    private final String newVersion;

    public DiffKey(DiffTask t) {
        this.task = t.getTask();
        this.gitlabProject = t.getGitlabProject();
        this.oldVersion = t.getOldVersion();
        this.newVersion = t.getNewVersion();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiffKey)) return false;
        DiffKey k = (DiffKey) o;
        return java.util.Objects.equals(task, k.task)
                && java.util.Objects.equals(gitlabProject, k.gitlabProject)
                && java.util.Objects.equals(oldVersion, k.oldVersion)
                && java.util.Objects.equals(newVersion, k.newVersion);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(task, gitlabProject, oldVersion, newVersion);
    }
}
