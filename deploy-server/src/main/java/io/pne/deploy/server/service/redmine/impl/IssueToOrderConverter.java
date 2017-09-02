package io.pne.deploy.server.service.redmine.impl;

import io.pne.deploy.server.model.OldCommand;
import io.pne.deploy.server.model.CommandState;
import io.pne.deploy.server.model.Order;
import io.pne.deploy.server.model.OrderState;
import io.pne.deploy.server.service.redmine.RedmineIssue;
import io.pne.deploy.server.util.Strings;

import java.util.*;

import static java.util.stream.Collectors.toList;

public class IssueToOrderConverter {

    public Order createOrder(RedmineIssue issue) {
        return new Order(
                UUID.randomUUID().toString()
                , issue.issueId()+""
                , createCommands(issue)
                , OrderState.CREATED
        );
    }

    private List<OldCommand> createCommands(RedmineIssue aIssue) {
        return Strings.split(aIssue.description(), "\n\r")
                .map(String::trim)
                .filter(line -> line.trim().startsWith("@"))
                .map(this::createCommand)
                .collect(toList());
    }

    public OldCommand createCommand(String aLine) {
        try {
            String commandId = UUID.randomUUID().toString();

            StringTokenizer tokenizer = new StringTokenizer(aLine, "\t ");
            String name = tokenizer.nextToken();

            Map<String, String> map = new HashMap<>();
            while (tokenizer.hasMoreTokens()) {
                String text = tokenizer.nextToken();
                StringTokenizer st = new StringTokenizer(text, "=");
                String key = nextToken(st, text, "key");
                String value = nextToken(st, text, "value");
                map.put(key, value);
            }

            return new OldCommand(commandId, name, map, CommandState.CREATED);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse '" + aLine + "'", e);
        }
    }

    private String nextToken(StringTokenizer st, String text, String key) {
        if (st.hasMoreTokens()) {
            return st.nextToken();
        }

        throw new IllegalStateException("No token for " + key + " in '" + text+"'");
    }

}
