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

    /** A version is a short line; anything beyond this is not one and must not be buffered. */
    private static final long MAX_RESPONSE_BYTES = 64 * 1024;

    public static String fetch(String aUrl) throws IOException {
        return fetch(aUrl, DEFAULT_TIMEOUT_SECONDS);
    }

    public static String fetch(String aUrl, int aTimeoutSeconds) throws IOException {
        int  timeoutMillis = aTimeoutSeconds * 1000;
        // One deadline for the whole request. Timing the headers and the body separately would let a slow answer
        // take twice as long as the step asked for.
        long deadline      = System.currentTimeMillis() + timeoutMillis;
        URL url = new URL(aUrl);
        URLConnection con = url.openConnection();
        con.setConnectTimeout(timeoutMillis);
        con.setReadTimeout(timeoutMillis);
        HttpURLConnection http = con instanceof HttpURLConnection ? (HttpURLConnection) con : null;
        if (http != null) {
            http.setInstanceFollowRedirects(false);
            int status = http.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                closeQuietly(http.getErrorStream());
                http.disconnect();
                throw new IOException(url + " answered " + status + " " + http.getResponseMessage());
            }
        }

        try (InputStream in = con.getInputStream()) {
            return readFirstLine(url, in, deadline);
        } finally {
            if (http != null) {
                http.disconnect();
            }
        }
    }

    /**
     * Reads the first non-empty line, and no more than a version could possibly be.
     *
     * <p>A {@link Scanner} keeps a read failure to itself and looks like the end of the input, so a timeout half
     * way through would otherwise be reported as a perfectly good version. The failure is asked for explicitly,
     * and the amount read is capped so that an endpoint answering with something enormous cannot exhaust memory.
     */
    private static String readFirstLine(URL aUrl, InputStream aInput, long aDeadline) throws IOException {
        try (StreamDeadline ignored = StreamDeadline.closeAt(aInput, aDeadline)) {
            Scanner scanner = new Scanner(new BoundedInputStream(aInput, MAX_RESPONSE_BYTES), "UTF-8");
            String  found   = null;
            while (found == null && scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (!line.isEmpty()) {
                    found = line;
                }
            }
            if (scanner.ioException() != null) {
                throw new IOException("Cannot read " + aUrl + ": " + scanner.ioException().getMessage(),
                        scanner.ioException());
            }
            if (found == null) {
                throw new IllegalStateException("No content for url " + aUrl);
            }
            return found;
        }
    }

    private static void closeQuietly(InputStream aStream) {
        if (aStream == null) {
            return;
        }
        try {
            aStream.close();
        } catch (IOException e) {
            // Nothing useful can be done about a stream that will be discarded anyway.
        }
    }
}
