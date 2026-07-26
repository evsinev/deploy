package io.pne.deploy.agent.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Properties;

/** The agent's own build version, baked into {@code agent-version.properties} at build time (from the git tag). */
public final class AgentVersion {

    private static final Logger LOG     = LoggerFactory.getLogger(AgentVersion.class);
    private static final String VERSION = load();

    private AgentVersion() {
    }

    public static String get() {
        return VERSION;
    }

    private static String load() {
        try (InputStream in = AgentVersion.class.getResourceAsStream("/agent-version.properties")) {
            if (in == null) {
                LOG.warn("agent-version.properties not found on the classpath");
                return "";
            }
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("version", "").trim();
        } catch (Exception e) {
            LOG.warn("Can't read the agent version", e);
            return "";
        }
    }
}
