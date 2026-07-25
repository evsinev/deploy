package io.pne.deploy.agent.commands;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;

/**
 * Reads the current application version from a URL the agent can reach (the deploy-server is often firewalled
 * off from it). Returns the first non-empty line; throws on a connection/HTTP error.
 */
public final class VersionFetcher {

    private VersionFetcher() {
    }

    public static String fetch(String aUrl) throws IOException {
        URL url = new URL(aUrl);
        URLConnection con = url.openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(10_000);
        try (InputStream in = con.getInputStream()) {
            Scanner scanner = new Scanner(in, "UTF-8");
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (!line.isEmpty()) {
                    return line;
                }
            }
            throw new IllegalStateException("No content for url " + url);
        }
    }
}
