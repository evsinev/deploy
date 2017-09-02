package io.pne.deploy.server.dao;

import io.pne.deploy.server.model.CommandState;

public interface ICommandsDao {

    boolean changeState(String aCommandId, CommandState aOldState, CommandState aNewState);
}
