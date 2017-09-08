package io.pne.deploy.agent.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;
import java.util.StringTokenizer;

public class CheckVersionCommand {

    private static final Logger LOG = LoggerFactory.getLogger(CheckVersionCommand.class);

    public static void main(String[] args) throws IOException {
        String versionUrl = args[0];
        String newVersion = args[1];

        String currentVersion = getUrlContent(versionUrl);
        LOG.info("Current version = {}", currentVersion);

        int compareResult = compareVersions(newVersion, currentVersion);
        String sign = getSign(compareResult);
        LOG.info("{} {} {}", newVersion, sign, currentVersion);

        if(compareResult < 0) {
            LOG.error("FAILED: New version must be greater or equals");
            System.exit(1);
        }
    }

    private static String getSign(int aCompare) {
        if(aCompare == 0) {
            return "=";
        }

        return aCompare > 0 ? ">" : "<";
    }

    private static int compareVersions(String aLeftVersion, String aRightVersion) {
        String versionDelimiters = ".-_;, ";
        StringTokenizer leftTokenizer = new StringTokenizer(aLeftVersion, versionDelimiters);
        StringTokenizer rightTokenizer = new StringTokenizer(aRightVersion, versionDelimiters);

        while(leftTokenizer.hasMoreTokens() && rightTokenizer.hasMoreTokens()) {
            int leftNumber = Integer.parseInt(leftTokenizer.nextToken());
            int rightNumber = Integer.parseInt(rightTokenizer.nextToken());

            if(leftNumber != rightNumber) {
                return leftNumber - rightNumber;
            }
        }

        if(leftTokenizer.hasMoreTokens()) {
            throw new IllegalStateException(aLeftVersion + " has more tokens than " + aRightVersion);
        }

        if(rightTokenizer.hasMoreTokens()) {
            throw new IllegalStateException(aRightVersion + " has more tokens than " + aLeftVersion);
        }

        return 0;
    }

    private static String getUrlContent(String aUrl) throws IOException {
        LOG.info("Loading {}", aUrl);
        URL url = new URL(aUrl);
        URLConnection con = url.openConnection();
        InputStream in = con.getInputStream();
        if(in == null) {
            throw new IllegalStateException("Input stream is null for " + url);
        }
        try {
            Scanner scanner = new Scanner(in, "utf-8");
            if(scanner.hasNextLine()) {
                return scanner.nextLine();
            } else {
                throw new IllegalStateException("No content for url " + url);
            }
        } finally {
            in.close();
        }
    }
}
