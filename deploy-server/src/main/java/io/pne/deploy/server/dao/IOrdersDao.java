package io.pne.deploy.server.dao;

import io.pne.deploy.server.model.Order;

public interface IOrdersDao {

    void saveOrder(Order aOrder);
}
