package io.pne.deploy.agent.commands;

import java.io.IOException;
import java.util.StringTokenizer;
import java.util.function.Consumer;

/**
 * Compares dotted version strings and refuses a deploy that would move an application backwards.
 *
 * <p>Both versions must have the same number of parts; a mismatch is an error rather than a guess, because guessing
 * would let a malformed version pass a check it should not.
 */
public final class VersionChecks {

    private static final String VERSION_DELIMITERS = ".-_;, ";

    private VersionChecks() {
    }

    /** Negative when the left version is older, zero when equal, positive when newer. */
    public static int compareVersions(String aLeftVersion, String aRightVersion) {
        StringTokenizer leftTokenizer  = new StringTokenizer(aLeftVersion,  VERSION_DELIMITERS);
        StringTokenizer rightTokenizer = new StringTokenizer(aRightVersion, VERSION_DELIMITERS);

        while (leftTokenizer.hasMoreTokens() && rightTokenizer.hasMoreTokens()) {
            int leftNumber  = Integer.parseInt(leftTokenizer.nextToken());
            int rightNumber = Integer.parseInt(rightTokenizer.nextToken());

            if (leftNumber != rightNumber) {
                return leftNumber - rightNumber;
            }
        }

        if (leftTokenizer.hasMoreTokens()) {
            throw new IllegalStateException(aLeftVersion + " has more tokens than " + aRightVersion);
        }

        if (rightTokenizer.hasMoreTokens()) {
            throw new IllegalStateException(aRightVersion + " has more tokens than " + aLeftVersion);
        }

        return 0;
    }

    public static String sign(int aCompareResult) {
        if (aCompareResult == 0) {
            return "=";
        }
        return aCompareResult > 0 ? ">" : "<";
    }

    /**
     * Reads the version an application currently reports and fails when {@code aNewVersion} is older.
     *
     * @throws IOException           the current version cannot be read
     * @throws IllegalStateException the new version is older than the current one
     */
    public static void checkNotOlder(String aVersionUrl, String aNewVersion, Consumer<String> aLog) throws IOException {
        String currentVersion = VersionFetcher.fetch(aVersionUrl);
        aLog.accept("current version is " + currentVersion);

        int compareResult = compareVersions(aNewVersion, currentVersion);
        aLog.accept(aNewVersion + " " + sign(compareResult) + " " + currentVersion);

        if (compareResult < 0) {
            throw new IllegalStateException("New version " + aNewVersion
                    + " must be greater than or equal to the current version " + currentVersion);
        }
    }
}
