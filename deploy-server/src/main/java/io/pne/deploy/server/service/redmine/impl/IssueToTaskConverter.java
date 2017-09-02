package io.pne.deploy.server.service.redmine.impl;

import io.pne.deploy.server.service.redmine.RedmineComment;
import io.pne.deploy.server.service.redmine.RedmineIssue;

import java.util.*;

public class IssueToTaskConverter {

    private final Set<Long> users;
    private final RedmineIssue issue;
    private List<String> hosts;
    private String command;
    private Map<String, String> parameters;

    public IssueToTaskConverter(Set<Long> users, RedmineIssue issue) {
        this.users = users;
        this.issue = issue;
    }

    public boolean isIssueApproved() {
        // create userId=false map
        Map<Long, Boolean> map = new HashMap<>();
        for (Long user : users) {
            map.put(user, false);
        }

        // if comment has OK then userId=true
        for (RedmineComment comment : issue.comments()) {
            if (comment.text().toUpperCase().contains("OK")) {
                map.put(comment.userId(), true);
            }
        }

        // map must contains all approved
        for (Boolean approved : map.values()) {
            if (!approved) {
                return false;
            }
        }

        return parse();
    }

    public boolean parse() {
        List<String> lines = tokenizeToList(issue.description(), "\n\r");
        for (String line : lines) {
            if (line.startsWith("@")) {
                StringTokenizer st = new StringTokenizer(line, " @\t");
                try {
                    command    = st.nextToken();
                    parameters = parseParameters(st);
                    hosts      = extractHosts(parameters);
                    return true;
                } catch (Exception e) {
                    throw new IllegalStateException("Could not parse '"+line+"'", e);
                }
            }
        }

        return false;
    }

    private List<String> extractHosts(Map<String, String> aParameters) {
        String hosts = aParameters.get("hosts");
        if (hosts != null) {
            aParameters.remove("hosts");
        }
        return tokenizeToList(hosts, "=");
    }


    private Map<String, String> parseParameters(StringTokenizer aStringTokenizer) {
        Map<String, String> map = new HashMap<>();
        while (aStringTokenizer.hasMoreTokens()) {
            String text = aStringTokenizer.nextToken();
            StringTokenizer st = new StringTokenizer(text, "=");
            String key = nextToken(st, text, "key");
            String value = nextToken(st, text, "value");
            map.put(key, value);
        }
        return map;
    }

    private String nextToken(StringTokenizer st, String text, String key) {
        if (st.hasMoreTokens()) {
            return st.nextToken();
        }

        throw new IllegalStateException("No token for " + key + " in '" + text+"'");
    }

    public List<String> getHosts() {
        return hosts;
    }

    public String getCommand() {
        return command;
    }

    public Map<String, ? extends String> getParameters() {
        return parameters;
    }

    private static List<String> tokenizeToList(String aText, String aTokens) {
        List<String> list = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(aText, aTokens);
        while (st.hasMoreTokens()) {
            list.add(st.nextToken().trim());
        }
        return list;
    }

}
