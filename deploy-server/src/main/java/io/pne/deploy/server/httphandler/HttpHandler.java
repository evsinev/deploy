package io.pne.deploy.server.httphandler;

import io.pne.deploy.server.bus.IBus;
import io.pne.deploy.server.bus.handlers.client_command.ClientCommandAction;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

public class HttpHandler implements Handler<HttpServerRequest> {

    private final IBus bus;

    public HttpHandler(IBus aBus) {
        bus = aBus;
    }

    @Override
    public void handle(HttpServerRequest aRequest) {

        HttpServerResponse response = aRequest.response();
        response.setChunked(true);

        aRequest.setExpectMultipart(true);
        aRequest.endHandler(event -> {
            ValidatedRequest request = new ValidatedRequest(aRequest);

            ClientCommandAction action = new ClientCommandAction(
                    request.getRequiredParameter("issue")
                    , request.getRequiredParameter("command")
                    , response
            );

            bus.send(action);

//            response.write("uri = "  + aRequest.uri());
//            response.write("\n");
//            response.write("uri = "  + aRequest.uri());
//            response.write("\n");
        });

    }
}
