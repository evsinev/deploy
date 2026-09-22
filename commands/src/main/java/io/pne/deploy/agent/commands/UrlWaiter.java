package io.pne.deploy.agent.commands;

import java.util.function.Consumer;

/**
 * Waits until an application reports the version that was just deployed.
 *
 * <p>An unreachable application is expected while it restarts, so that is retried until the deadline. An application
 * that answers with a different version is not: it means the restart did not pick up the new version, and waiting
 * longer would only delay the report. Callers that restart an application which keeps answering during the restart
 * can switch that off with {@code aFailOnOtherContent}.
 */
public final class UrlWaiter {

    private UrlWaiter() {
    }

    public static void waitFor(
              String           aUrl
            , String           aExpectedContent
            , int              aSecondsToWait
            , int              aIntervalSeconds
            , boolean          aFailOnOtherContent
            , Consumer<String> aLog
    ) throws InterruptedException {

        long intervalMillis = Math.max(1, aIntervalSeconds) * 1000L;
        long endTime        = System.currentTimeMillis() + aSecondsToWait * 1000L;
        String lastContent  = null;

        while (System.currentTimeMillis() < endTime) {
            try {
                String content = VersionFetcher.fetch(aUrl);
                lastContent = content;
                if (aExpectedContent.equals(content)) {
                    aLog.accept(aUrl + " reports " + content);
                    return;
                }
                aLog.accept(aUrl + " reports " + content + ", waiting for " + aExpectedContent);
                if (aFailOnOtherContent) {
                    throw new IllegalStateException(aUrl + " reports " + content
                            + " but " + aExpectedContent + " was expected");
                }
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                aLog.accept(aUrl + " is not answering yet: " + e.getMessage());
            }
            // Never sleep past the deadline: a long interval must not stretch a short wait.
            long remaining = endTime - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            Thread.sleep(Math.min(intervalMillis, remaining));
        }

        throw new IllegalStateException("Timed out after " + aSecondsToWait + "s waiting for " + aUrl
                + " to report " + aExpectedContent
                + (lastContent == null ? "; it never answered" : "; it last reported " + lastContent));
    }
}
