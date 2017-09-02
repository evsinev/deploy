package io.pne.deploy.server.service.redmine;

import io.pne.deploy.server.model.Order;
import io.pne.deploy.server.task.Task;

import java.util.List;

public interface IRedmineService {
//    List<Task> getAssignedTasks();

    List<Order> processAssignedTickets();

}
