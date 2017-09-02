package io.pne.deploy.server.model;

import java.util.List;

public class Order {

    public final String        orderId;
    public final String        issue;
    public final List<OldCommand> commands;
    public final OrderState    orderState;

    public Order(String orderId, String issue, List<OldCommand> commands, OrderState orderState) {
        this.orderId = orderId;
        this.issue = issue;
        this.commands = commands;
        this.orderState = orderState;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", issue='" + issue + '\'' +
                ", commands=" + commands +
                ", orderState=" + orderState +
                '}';
    }
}
