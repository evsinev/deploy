package io.pne.deploy.agent.websocket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.payneteasy.websocket.IWebSocketListener;
import com.payneteasy.websocket.WebSocketClient;
import com.payneteasy.websocket.WebSocketHandshakeRequest;
import com.payneteasy.websocket.WebSocketSession;
import io.pne.deploy.agent.api.IAgentChannelService;
import io.pne.deploy.agent.api.IAgentService;
import io.pne.deploy.agent.api.messages.RunAgentCommandLog;
import io.pne.deploy.agent.service.IAgentApplicationListener;
import io.pne.deploy.agent.service.IAgentStartupParameters;
import io.pne.deploy.agent.service.impl.AgentApplicationListenerNoOp;
import io.pne.deploy.agent.service.impl.AgentServiceImpl;
import io.pne.deploy.agent.service.impl.EnvAgentStartupParametersImpl;
import io.pne.deploy.agent.service.log.IAgentLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketException;

import static java.lang.Runtime.getRuntime;
import static java.lang.Thread.currentThread;

public class WebSocketAgentApplication {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketAgentApplication.class);

    private final    WebSocketClient           webSocketClient;
    private final    IWebSocketListener        webSocketListener;
    private volatile WebSocketSession          session;
    private final    IAgentApplicationListener agentListener;
    private final    IAgentStartupParameters   parameters;
    private final    WebSocketOutputQueue      queue;

    public static void main(String[] args) {
        EnvAgentStartupParametersImpl startupParameters = new EnvAgentStartupParametersImpl();
        AgentApplicationListenerNoOp  agentListener     = new AgentApplicationListenerNoOp();
        WebSocketAgentApplication     app               = new WebSocketAgentApplication(agentListener, startupParameters);
        app.start();
    }

    public WebSocketAgentApplication(IAgentApplicationListener aAgentListener, IAgentStartupParameters aParameters) {

        Gson              gson         = new GsonBuilder().setPrettyPrinting().create();
        queue             = new WebSocketOutputQueue(gson);
        IAgentChannelService agentChannelService = new WebSocketAgentChannelService(queue);
        IAgentLogService logService    = (aId, aText) -> agentChannelService.sendLog(new RunAgentCommandLog(aId, aText));
        IAgentService     agentService = new AgentServiceImpl(logService);

        parameters        = aParameters;
        agentListener     = aAgentListener;
        webSocketClient   = new WebSocketClient();
        webSocketListener = new WebSocketListenerImpl(agentService, gson, queue);
    }

    public void start() {
        addShutdownHook();

        while(!currentThread().isInterrupted()) {
            try {
                session = connectToServer();
                agentListener.didConnected();
                try {
                    session.startAndWait(webSocketListener);
                } catch (SocketException e) {
                    if("Socket closed".equals(e.getMessage())) {
                        LOG.warn("Socket closed");
                    } else {
                        LOG.error("Socket error", e);
                    }
                } catch (Exception e) {
                    LOG.error("Error in session", e);
                }
            } catch (Exception e) {
                LOG.error("Error while connecting to server " + parameters.getServerBaseUrl(), e);
            }

            session = null;

            try {
                LOG.info("Sleeping 1 second ...");
                Thread.sleep(1_000);
            } catch (InterruptedException e) {
                LOG.error("Interrupted, exiting ...");
                return;
            }
        }

    }

    public void stop() {
        LOG.info("Closing session {}", session);
        if(session != null) {
            try {
                session.close();
            } catch (IOException e) {
                LOG.error("Could not close session", e);
            }
        }
    }

    private WebSocketSession connectToServer() throws IOException {
        WebSocketHandshakeRequest request = new WebSocketHandshakeRequest.Builder()
                .url(parameters.getServerBaseUrl() + parameters.getAgentId())
                .build();

        return webSocketClient.connect(request, queue);
    }

    private void addShutdownHook() {
        getRuntime().addShutdownHook(new Thread(this::stop));
    }

}
