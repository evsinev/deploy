package io.pne.deploy.server.service.command;

import io.pne.deploy.server.model.OldCommand;

public interface ICommandService {

    void scheduleCommand(OldCommand aCommand);
}
