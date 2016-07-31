package io.pne.deploy.server.bus.handlers.text_order;

import io.pne.deploy.server.bus.IAction;

import java.util.List;

public class TextOrderAction implements IAction {

    public final String       issue;
    public final List<CommandLine> commands;

    public TextOrderAction(String issue, List<CommandLine> commands) {
        this.issue = issue;
        this.commands = commands;
    }

    @Override
    public String toString() {
        return "TextOrderAction{" +
                "issue='" + issue + '\'' +
                ", commands=" + commands +
                '}';
    }
}
