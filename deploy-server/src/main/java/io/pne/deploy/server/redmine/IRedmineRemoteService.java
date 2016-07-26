package io.pne.deploy.server.redmine;

import java.util.List;

public interface IRedmineRemoteService {

    List<RedmineIssue> listAssignedTickets();

}
