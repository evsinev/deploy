package io.pne.deploy.server.service.order;

import io.pne.deploy.server.model.Order;

import java.util.List;

public interface IOrderService {

    void schedulerOrders(List<Order> aOrders);

}
