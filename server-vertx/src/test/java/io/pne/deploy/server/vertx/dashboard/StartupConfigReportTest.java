package io.pne.deploy.server.vertx.dashboard;

import com.payneteasy.startup.parameters.StartupParametersFactory;
import io.pne.deploy.client.redmine.remote.impl.IRedmineRemoteConfig;
import io.pne.deploy.server.vertx.IVertxServerConfiguration;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StartupConfigReportTest {

    @Test
    public void reportsEntriesAndMasksSecrets() {
        List<StartupConfigReport.Entry> entries = StartupConfigReport.of(List.of(
                new StartupConfigReport.Group("Redmine", IRedmineRemoteConfig.class,
                        StartupParametersFactory.getStartupParameters(IRedmineRemoteConfig.class)),
                new StartupConfigReport.Group("Server", IVertxServerConfiguration.class,
                        StartupParametersFactory.getStartupParameters(IVertxServerConfiguration.class)),
                new StartupConfigReport.Group("Dashboard", IDashboardConfig.class,
                        StartupParametersFactory.getStartupParameters(IDashboardConfig.class))));

        assertTrue(has(entries, "REDMINE_URL"));
        assertTrue(has(entries, "VERTX_SERVER_PORT"));
        assertTrue(has(entries, "DASHBOARD_PATH"));

        assertTrue("REDMINE_API_ACCESS_KEY must be masked", masked(entries, "REDMINE_API_ACCESS_KEY"));
        assertTrue("TELEGRAM_TOKEN must be masked", masked(entries, "TELEGRAM_TOKEN"));
        assertTrue("GITLAB_API_KEY must be masked by the name rule", masked(entries, "GITLAB_API_KEY"));
        assertFalse("REDMINE_URL must not be masked", masked(entries, "REDMINE_URL"));
    }

    private static boolean has(List<StartupConfigReport.Entry> aEntries, String aName) {
        return aEntries.stream().anyMatch(e -> e.name().equals(aName));
    }

    private static boolean masked(List<StartupConfigReport.Entry> aEntries, String aName) {
        return aEntries.stream().anyMatch(e -> e.name().equals(aName) && e.masked());
    }
}
