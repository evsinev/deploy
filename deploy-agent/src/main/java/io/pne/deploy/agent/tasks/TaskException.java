package io.pne.deploy.agent.tasks;

import java.util.Optional;

public class TaskException extends Exception {

    private final Optional<String> privateMessage;

    public TaskException(String aPublicMessage, String aPrivateMessage, Exception aException) {
        super(aPublicMessage, aException);
        privateMessage = Optional.of(aPrivateMessage);
    }

    public TaskException(String aPublicMessage, Exception aException) {
        super(aPublicMessage, aException);
        privateMessage = Optional.empty();
    }

    public Optional<String> getPrivateMessage() {
        return privateMessage;
    }
}
