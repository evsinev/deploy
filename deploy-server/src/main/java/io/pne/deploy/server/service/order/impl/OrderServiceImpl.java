package io.pne.deploy.server.service.order.impl;

import io.pne.deploy.server.bus.IBus;
import io.pne.deploy.server.dao.ICommandsDao;
import io.pne.deploy.server.dao.IOrdersDao;
import io.pne.deploy.server.model.Command;
import io.pne.deploy.server.model.Order;
import io.pne.deploy.server.service.command.ICommandService;
import io.pne.deploy.server.service.order.IOrderService;

import java.util.List;

public class OrderServiceImpl implements IOrderService {

    private final IOrdersDao      ordersDao;
    private final IBus            bus;
    private final ICommandService commandService;

    public OrderServiceImpl(IOrdersDao ordersDao, IBus bus, ICommandService commandService) {
        this.ordersDao = ordersDao;
        this.bus = bus;
        this.commandService = commandService;
    }

    @Override
    public void schedulerOrders(List<Order> aOrders) {
        for (Order order : aOrders) {
            schedulerOrder(order);
        }
    }

    private void schedulerOrder(Order aOrder) {
        ordersDao.saveOrder(aOrder);

        for (Command command : aOrder.commands) {
            commandService.scheduleCommand(command);
        }

        // 1. put order to the queue
        // 2. if agent is online move order to it
        // 3. every 10 seconds checks agents
        // 4. every 10 seconds checks command status on agent
    }
}
