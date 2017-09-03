package io.pne.deploy.server.vertx;

import java.io.File;

public interface IVertxServerConfiguration {

    int getPort();

    File getAliasesDir();
}
