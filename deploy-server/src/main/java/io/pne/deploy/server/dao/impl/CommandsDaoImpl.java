package io.pne.deploy.server.dao.impl;

import io.pne.deploy.server.dao.ICommandsDao;
import io.pne.deploy.server.model.CommandState;

import java.io.File;

public class CommandsDaoImpl implements ICommandsDao {

    private final File commandsDir;

    public CommandsDaoImpl(File commandsDir) {
        this.commandsDir = commandsDir;
    }

    @Override
    public boolean changeState(String aCommandId, CommandState aOldState, CommandState aNewState) {
        synchronized (this) {

        }
        throw new IllegalStateException("not implemented yet");
    }
}
