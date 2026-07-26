package io.pne.deploy.client.redmine.remote;

import io.pne.deploy.client.redmine.process.data_model.DiffTask;
import io.pne.deploy.client.redmine.remote.data_model.DiffCommit;

import java.util.List;

public interface IRemoteGitlabService {
    List<DiffCommit> getTagDiff(DiffTask diffTask);
}
