package io.pne.deploy.client.redmine.remote.impl;

import com.payneteasy.startup.parameters.StartupParametersFactory;
import io.pne.deploy.client.redmine.process.data_model.DiffTask;
import io.pne.deploy.client.redmine.remote.IRemoteGitlabService;
import org.junit.Ignore;
import org.junit.Test;

public class RemoteGitlabServiceImplTest {
    @Ignore
    @Test
    public void getDiffTest() {
        IRedmineRemoteConfig config = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);
        IRemoteGitlabService gitlabService = new RemoteGitlabServiceImpl(config);
        DiffTask diffTask = new DiffTask(new String[0], 114, "test", "3.36.145-179","3.36.145-219");
        System.out.println(String.join("\\=,\\=", gitlabService.getTagDiff(diffTask)));
    }
}
