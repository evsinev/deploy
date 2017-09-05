package io.pne.deploy.server.vertx;

import io.pne.deploy.util.env.StartupConfig;

import java.io.File;

public class VertxServerConfigurationImpl implements IVertxServerConfiguration {

    private final StartupConfig config = new StartupConfig(IVertxServerConfiguration.class);

    @Override
    public int getPort() {
        return config.getInt("getPort");
    }

    @Override
    public File getAliasesDir() {
        return new File(config.get("getAliasesDir"));
    }
}
