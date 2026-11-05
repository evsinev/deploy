package io.pne.deploy.client.redmine.remote.impl;

import com.payneteasy.startup.parameters.StartupParametersFactory;
import io.pne.deploy.client.redmine.remote.IRemoteRedmineService;
import io.pne.deploy.client.redmine.remote.data_model.RedmineIssueData;
import io.pne.deploy.client.redmine.remote.model.RedmineIssue;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RemoteRedmine4_2_10ServiceImplTest {

    @Test
    public void mapIssueToleratesMissingCustomFieldsAndAssignee() {
        // Redmine omits custom_fields / assigned_to for issues that have none; must not NPE.
        RedmineIssueData data = RedmineIssueData.builder()
                .id(167994)
                .subject("Referenced issue")
                .build();
        RedmineIssue issue = RemoteRedmine4_2_10ServiceImpl.mapIssue(data);
        assertEquals(167994, issue.issueId());
        assertEquals("Referenced issue", issue.subject());
        assertTrue("custom fields default to empty", issue.customFields().isEmpty());
        assertEquals("assignee defaults to empty", "", issue.assigneeName());
    }
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
        redmineService.enqueueChangeStatusFromAcceptedToProcessing(124528, "changeStatusFromAcceptedToProcessingTest");
    }

    @Test
    @Ignore
    public void changeStatusToDoneTest() {
        IRedmineRemoteConfig config = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);
        IRemoteRedmineService redmineService = new RemoteRedmine4_2_10ServiceImpl(config);
        redmineService.enqueueChangeStatusToDone(124528, "changeStatusToDoneTest");
    }

    @Test
    @Ignore
    public void changeStatusToFailedTest() {
        IRedmineRemoteConfig config = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);
        IRemoteRedmineService redmineService = new RemoteRedmine4_2_10ServiceImpl(config);
        redmineService.enqueueChangeStatusToFailed(124528, "changeStatusToFailed");
    }

    @Test
    @Ignore
    public void addCommentTest() {
        IRedmineRemoteConfig config = StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class);
        IRemoteRedmineService redmineService = new RemoteRedmine4_2_10ServiceImpl(config);
        redmineService.enqueueChangeStatusToFailed(124528, "addCommentTest");
    }
}
