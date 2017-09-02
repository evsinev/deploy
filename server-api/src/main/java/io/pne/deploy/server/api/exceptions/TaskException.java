package io.pne.deploy.server.api.exceptions;

/**
 * Created by esinev on 02/09/17.
 */
public class TaskException extends Exception {

    public TaskException(String message) {
        super(message);
    }

    public TaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
