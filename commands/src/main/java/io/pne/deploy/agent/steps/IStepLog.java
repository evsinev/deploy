package io.pne.deploy.agent.steps;

/** Where a running plan writes its progress. Lines are streamed to the server as they appear. */
public interface IStepLog {

    void log(String aLine);
}
