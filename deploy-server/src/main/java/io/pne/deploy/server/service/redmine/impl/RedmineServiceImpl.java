package io.pne.deploy.server.service.redmine.impl;

import io.pne.deploy.server.model.Order;
import io.pne.deploy.server.dao.IIssuesDao;
import io.pne.deploy.server.service.redmine.IRedmineRemoteService;
import io.pne.deploy.server.service.redmine.IRedmineService;
import io.pne.deploy.server.service.redmine.RedmineIssue;
import io.pne.deploy.server.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toList;

public class RedmineServiceImpl implements IRedmineService {

    private static final Logger LOG = LoggerFactory.getLogger(RedmineServiceImpl.class);

    private final IRedmineRemoteService redmineRemoteService;
    private final IIssuesDao            issuesDao;
    private final IssueToOrderConverter issueToOrderConverter = new IssueToOrderConverter();

    public RedmineServiceImpl(IRedmineRemoteService aRemoteService, IIssuesDao aDao) {
        redmineRemoteService = aRemoteService;
        issuesDao            = aDao;
    }

    @Override
    public List<Order> processAssignedTickets() {
        List<RedmineIssue> issues = redmineRemoteService.listAssignedTickets();
        LOG.info("Fetched {} issues", issues.size());

        List<Order> orders = extractApprovedOrders(issues);
        LOG.info("Found {} new orders", orders.size());

        return orders;
    }

    private List<Order> extractApprovedOrders(List<RedmineIssue> aIssues) {

        Predicate<RedmineIssue> hasCommand = aIssue -> Strings
                .split(aIssue.description(), "\n\r")
                .anyMatch(line -> line.trim().startsWith("@"));

        return aIssues.stream()
                .filter(issue -> issue.comments().size() == 3)
                .filter(issuesDao::isNewIssue)
                .filter(hasCommand)
                .map(issueToOrderConverter::createOrder)
                .collect(toList());
    }

}
