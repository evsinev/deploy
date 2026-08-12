package io.pne.deploy.server.api.task;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
@Builder
public class TaskDiff {

    boolean enabled;
    String  versionUrl;
    int     gitlabProjectId;
    String  agent;
    int     newVersionArg; // 1-based index of the task-line argument that carries the new version

    // Deploy-review webhook identity: GitLab project path (e.g. payneteasy/paynet), application name
    // (defaults to the alias name when omitted) and the target instance (e.g. AMS-2).
    String  project;
    String  app;
    String  instance;

}
