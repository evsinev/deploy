package io.pne.deploy.client.redmine.process;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import static lombok.AccessLevel.PRIVATE;

@Data
@FieldDefaults(makeFinal = true, level = PRIVATE)
@AllArgsConstructor(access = PRIVATE)
public class ProcessRedmineIssueResult {
    ProcessRedmineIssueResultType type;
    String                        errorMessage;

    public static ProcessRedmineIssueResult notValidated() {
        return new ProcessRedmineIssueResult(ProcessRedmineIssueResultType.NO_VALIDATED, null);
    }

    public static ProcessRedmineIssueResult success() {
        return new ProcessRedmineIssueResult(ProcessRedmineIssueResultType.SUCCESS, null);
    }

    public static ProcessRedmineIssueResult failure(String errorMessage) {
        return new ProcessRedmineIssueResult(ProcessRedmineIssueResultType.FAILURE, errorMessage);
    }
}
