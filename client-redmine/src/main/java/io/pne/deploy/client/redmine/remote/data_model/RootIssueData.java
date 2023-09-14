package io.pne.deploy.client.redmine.remote.data_model;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
@Builder
public class RootIssueData {

    RedmineIssueData issue;

    public RootIssueData(RedmineIssueData issue) {
        this.issue = issue;
    }
}

