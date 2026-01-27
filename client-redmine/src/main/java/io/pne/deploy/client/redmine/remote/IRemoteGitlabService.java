package io.pne.deploy.client.redmine.remote;

import io.pne.deploy.client.redmine.process.data_model.DiffTask;

import java.util.List;

public interface IRemoteGitlabService {
    List<String> getTagDiff(DiffTask diffTask);
}
