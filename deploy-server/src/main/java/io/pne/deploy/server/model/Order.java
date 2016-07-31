package io.pne.deploy.server.model;

import java.util.List;

public class Order {

    public final String        orderId;
    public final String        issue;
    public final List<Command> commands;

    public Order(String aOrderId, String issue, List<Command> commands) {
        orderId = aOrderId;
        this.issue = issue;
        this.commands = commands;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", issue='" + issue + '\'' +
                ", commands=" + commands +
                '}';
    }
}
