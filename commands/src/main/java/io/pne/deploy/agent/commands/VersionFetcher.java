package io.pne.deploy.agent.commands;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;

/**
 * Reads the current application version from a URL the agent can reach (the deploy-server is often firewalled
 * off from it). Returns the first non-empty line; throws on a connection/HTTP error.
 *
 * <p>Redirects are not followed. Only the URL that was checked against the policy is fetched, so an application
 * answering with a redirect cannot send the agent to a host nobody allowed.
 */
public final class VersionFetcher {

    private VersionFetcher() {
    }

    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    public static String fetch(String aUrl) throws IOException {
        return fetch(aUrl, DEFAULT_TIMEOUT_SECONDS);
    }

    public static String fetch(String aUrl, int aTimeoutSeconds) throws IOException {
        int timeoutMillis = aTimeoutSeconds * 1000;
        URL url = new URL(aUrl);
        URLConnection con = url.openConnection();
        con.setConnectTimeout(timeoutMillis);
        con.setReadTimeout(timeoutMillis);
        if (con instanceof HttpURLConnection) {
            HttpURLConnection http = (HttpURLConnection) con;
            http.setInstanceFollowRedirects(false);
            int status = http.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException(url + " answered " + status + " " + http.getResponseMessage());
            }
        }
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
