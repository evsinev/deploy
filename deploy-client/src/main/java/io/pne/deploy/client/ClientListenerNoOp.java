package io.pne.deploy.client;

public class ClientListenerNoOp implements IClientListener {

    @Override
    public void didReceiveLine(String aLine) {
        System.out.println(aLine);
    }
}
