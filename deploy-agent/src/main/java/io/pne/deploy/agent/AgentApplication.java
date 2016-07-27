package io.pne.deploy.agent;

import com.payneteasy.websocket.IWebSocketListener;
import com.payneteasy.websocket.WebSocketClient;
import com.payneteasy.websocket.WebSocketHandshakeRequest;
import com.payneteasy.websocket.WebSocketSession;
import io.pne.deploy.agent.tasks.ITaskService;
import io.pne.deploy.agent.tasks.impl.TaskServiceImpl;
import io.pne.deploy.agent.websocket.MessageSenderImpl;
import io.pne.deploy.agent.websocket.WebSocketFrameListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketException;

import static java.lang.Runtime.getRuntime;
import static java.lang.Thread.currentThread;

public class AgentApplication {

    private static final Logger LOG = LoggerFactory.getLogger(AgentApplication.class);

    private final    WebSocketClient    client;
    private final    IWebSocketListener listener;
    private volatile WebSocketSession   session;
    private final    MessageSenderImpl  sender;
    private final    IAgentListener     agentListener;

    public AgentApplication(IAgentListener aAgentListener) {
        ITaskService   taskService = new TaskServiceImpl();

        agentListener = aAgentListener;
        sender        = new MessageSenderImpl();
        listener      = new WebSocketFrameListenerImpl(taskService, sender, aAgentListener);
        client        = new WebSocketClient();
    }

    public void start() {

        addShutdownHook();

        while(!currentThread().isInterrupted()) {
            try {
                session = connectToServer();
                agentListener.didConnected();
                try {
                    session.startAndWait(listener);
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
                LOG.error("Error while connecting to server", e);
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


    private void addShutdownHook() {
        getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                LOG.info("Closing session {}", session);
                if(session != null) {
                    try {
                        session.close();
                    } catch (IOException e) {
                        LOG.error("Could not close session", e);
                    }
                }
            }
        });

    }

    private WebSocketSession connectToServer() throws IOException {
        WebSocketHandshakeRequest request = new WebSocketHandshakeRequest.Builder()
                .url("http://localhost:9090/java")
                .build();

        return client.connect(request, sender);
    }

    public static void main(String[] args) throws IOException {
        new AgentApplication(new AgentListenerNoOp()).start();
    }

}
