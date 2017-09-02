package io.pne.deploy.server.service.redmine;

import java.util.List;

public interface IRedmineRemoteService {

    List<RedmineIssue> listAssignedTickets();

}
