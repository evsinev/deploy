package io.pne.deploy.server.service.command;

import io.pne.deploy.server.model.Command;

public interface ICommandService {

    void scheduleCommand(Command aCommand);
}
