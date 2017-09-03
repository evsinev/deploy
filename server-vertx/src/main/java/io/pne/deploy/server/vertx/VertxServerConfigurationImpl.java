package io.pne.deploy.server.vertx;

import java.io.File;

public class VertxServerConfigurationImpl implements IVertxServerConfiguration {

    @Override
    public int getPort() {
        return 8080;
    }

    @Override
    public File getAliasesDir() {
        return new File("aliases");
    }
}
