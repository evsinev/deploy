package io.pne.deploy.server.redmine.impl;

import com.taskadapter.redmineapi.*;
import com.taskadapter.redmineapi.bean.Issue;
import io.pne.deploy.server.redmine.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RedmineRemoteServiceImpl implements IRedmineRemoteService {

    private final IssueManager issueManager;

    public RedmineRemoteServiceImpl(String aRedmineUrl, String aApiAccessKey) {
        RedmineManager redmine = RedmineManagerFactory.createWithApiKey(aRedmineUrl,  aApiAccessKey);
        issueManager = redmine.getIssueManager();
    }

    @Override
    public List<RedmineIssue> listAssignedTickets() {
        return getRedmineIssues().stream()
                .map(issue -> ImmutableRedmineIssue.builder()
                        .issueId     ( issue.getId()              )
                        .description ( issue.getDescription()     )
                        .comments    ( getComments(issue.getId()) )
                        .build())
                .collect(Collectors.<RedmineIssue>toList());
    }

    private List<RedmineComment> getComments(int aIssueId) {
        Issue issue;
        try {
            issue = issueManager.getIssueById(aIssueId, Include.journals);
        } catch (RedmineException e) {
            throw new IllegalStateException("Could not get comments for issue "+aIssueId, e);
        }
        return issue.getJournals().stream()
                .map(journal -> ImmutableRedmineComment.builder()
                        .userId ( journal.getUser().getId() )
                        .text   ( journal.getNotes()        )
                        .build())
                .collect(Collectors.<RedmineComment>toList());
    }

    private List<Issue> getRedmineIssues() {

        Map<String, String> parameters = new HashMap<>();
        parameters.put("assigned_to_id", "me");
        parameters.put("include", "changesets");
        try {
            List<Issue> issues = issueManager.getIssues(parameters);
            for (Issue issue : issues) {
                System.out.println("subject: " + issue.getSubject());
                System.out.println("id     : " + issue.getId());
                System.out.println("text   : " + issue.getDescription());
            }
            return issues;
        } catch (RedmineException e) {
            throw new IllegalStateException("Could not get issues", e);
        }
    }
}
