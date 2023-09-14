package io.pne.deploy.client.redmine.remote.impl;

import com.payneteasy.startup.parameters.StartupParametersFactory;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import org.junit.Ignore;
import org.junit.Test;

public class RemoteRedmine4_2_10ServiceImplTest {
    @Test
    @Ignore
    public void getCommentTest() {
        IRedmineRemoteConfig config = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);
        IRemoteRedmineService redmineService = new RemoteRedmine4_2_10ServiceImpl(config);
        System.out.println(redmineService.getComments(125637));
    }

    @Test
    @Ignore
    public void getIssueTest() {
        IRedmineRemoteConfig config = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);
        IRemoteRedmineService redmineService = new RemoteRedmine4_2_10ServiceImpl(config);
        System.out.println(redmineService.getIssue(125637));
    }

    @Test
    @Ignore
    public void changeStatusFromAcceptedToProcessingTest() {
        IRedmineRemoteConfig config = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);
        IRemoteRedmineService redmineService = new RemoteRedmine4_2_10ServiceImpl(config);
        redmineService.changeStatusFromAcceptedToProcessing(124528, "changeStatusFromAcceptedToProcessingTest");
    }

    @Test
    @Ignore
    public void changeStatusToDoneTest() {
        IRedmineRemoteConfig config = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);
        IRemoteRedmineService redmineService = new RemoteRedmine4_2_10ServiceImpl(config);
        redmineService.changeStatusToDone(124528, "changeStatusToDoneTest");
    }

    @Test
    @Ignore
    public void changeStatusToFailedTest() {
        IRedmineRemoteConfig config = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);
        IRemoteRedmineService redmineService = new RemoteRedmine4_2_10ServiceImpl(config);
        redmineService.changeStatusToFailed(124528, "changeStatusToFailed");
    }

    @Test
    @Ignore
    public void addCommentTest() {
        IRedmineRemoteConfig config = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);
        IRemoteRedmineService redmineService = new RemoteRedmine4_2_10ServiceImpl(config);
        redmineService.changeStatusToFailed(124528, "addCommentTest");
    }
}
