package io.pne.deploy.server.service.impl.alias;

/** Optional {@code diff:} block of an alias (mutable so SnakeYAML can populate it); mapped to TaskDiff. */
public class AliasDiff {
    public boolean enabled;
    public String  versionUrl;
    public int     gitlabProjectId;
    public String  agent;
}
