package io.pne.deploy.server.dao.impl;

import io.pne.deploy.server.dao.IOrdersDao;
import io.pne.deploy.server.model.Order;

import java.io.File;

public class OrdersDaoImpl implements IOrdersDao {

    private final File odersDir;

    public OrdersDaoImpl(File odersDir) {
        this.odersDir = odersDir;
    }

    @Override
    public void saveOrder(Order aOrder) {
        // todo save orders to the base dir
    }
}
