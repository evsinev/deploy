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

}
