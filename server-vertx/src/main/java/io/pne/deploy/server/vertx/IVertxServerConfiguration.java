package io.pne.deploy.server.vertx;

import io.pne.deploy.util.env.AStartupParameter;
import io.pne.deploy.util.env.IStartupConfig;

import java.io.File;

public interface IVertxServerConfiguration extends IStartupConfig {

    @AStartupParameter(name = "VERTX_SERVER_PORT", defaultValue = "8080")
    int getPort();

    @AStartupParameter(name = "VERTX_ALIASES_DIR", defaultValue = "./aliases")
    File getAliasesDir();
}
