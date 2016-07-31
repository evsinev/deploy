package io.pne.deploy.server.bus.handlers.order;

import io.pne.deploy.server.bus.IAction;
import io.pne.deploy.server.model.Order;

public class OrderAction implements IAction {

    public final Order order;

    public OrderAction(Order order) {
        this.order = order;
    }

    @Override
    public String toString() {
        return "OrderAction{" +
                "order=" + order +
                '}';
    }
}
