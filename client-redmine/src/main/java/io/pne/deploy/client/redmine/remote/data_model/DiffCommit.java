package io.pne.deploy.client.redmine.remote.data_model;

import lombok.Data;

import java.time.LocalDate;

/** One GitLab commit reduced to what the diff report needs: the message and the committer date. */
@Data
public class DiffCommit {
    private final String message;
    private final LocalDate date;
}
