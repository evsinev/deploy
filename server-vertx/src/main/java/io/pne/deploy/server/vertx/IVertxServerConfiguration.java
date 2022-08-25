package io.pne.deploy.server.vertx;


import com.payneteasy.startup.parameters.AStartupParameter;
import io.pne.deploy.util.env.IStartupConfig;

import java.io.File;

public interface IVertxServerConfiguration extends IStartupConfig {

    @AStartupParameter(name = "VERTX_SERVER_PORT", value = "8080")
    int getPort();

    @AStartupParameter(name = "VERTX_ALIASES_DIR", value = "./aliases")
    File getAliasesDir();
}
