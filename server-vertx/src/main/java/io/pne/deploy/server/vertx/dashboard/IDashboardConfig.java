package io.pne.deploy.server.vertx.dashboard;

import com.payneteasy.startup.parameters.AStartupParameter;
import io.pne.deploy.util.env.IStartupConfig;

public interface IDashboardConfig extends IStartupConfig {

    @AStartupParameter(name = "DASHBOARD_PATH", value = "/deploy/dashboard")
    String path();

    @AStartupParameter(name = "DASHBOARD_REFRESH_MS", value = "2000")
    long refreshMs();
}
