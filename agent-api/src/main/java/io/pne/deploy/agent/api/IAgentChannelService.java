package io.pne.deploy.agent.api;

import io.pne.deploy.agent.api.messages.RunAgentCommandLog;

/**
 * Asynchronously send from agent to server
 */
public interface IAgentChannelService {

    void sendLog(RunAgentCommandLog aLogMessage);
}
