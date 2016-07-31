package io.pne.deploy.server.httphandler;

import io.vertx.core.http.HttpServerRequest;

public class ValidatedRequest {

    private final HttpServerRequest request;

    public ValidatedRequest(HttpServerRequest request) {
        this.request = request;
    }

    public String getRequiredParameter(String aName) {
        String value = request.getParam(aName);
        if(value == null) {
            value = request.getFormAttribute(aName);
        }
        if(value == null) {
            throw new IllegalStateException(aName + " not found neither GET parameters " + request.params()
                    + " nor " + request.formAttributes()
            );
        }

        return value;
    }
}
