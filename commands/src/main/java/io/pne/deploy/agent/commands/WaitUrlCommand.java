package io.pne.deploy.agent.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class WaitUrlCommand {

    private static final Logger LOG = LoggerFactory.getLogger(WaitUrlCommand.class);

    public static void main(String[] args) throws InterruptedException {
        String versionUrl = args[0];
        String content    = args[1];
        int secondsToWait = Integer.parseInt(args[2]);

        long endTime = System.currentTimeMillis() + secondsToWait * 1000;


        while ((endTime > System.currentTimeMillis())) {
            try {
                String version = getUrlContent(versionUrl);
                LOG.info("Content = {}", version);
                if (version.equals(content)) {
                    System.exit(0);
                } else {
                    LOG.error("Wrong content. must be {}", content);
                    System.exit(1);
                }
            } catch (Exception e) {
                LOG.error(e.getMessage());
                Thread.sleep(1_000);
            }
        }

        LOG.error("Time out for {}", versionUrl);
        System.exit(1);
    }


    private static String getUrlContent(String aUrl) throws IOException {
        LOG.info("Loading {}", aUrl);
        URL url = new URL(aUrl);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        if(200 != con.getResponseCode()) {
            throw new IllegalStateException("Response code is " + 200+", " + con.getResponseMessage());
        }

        InputStream in = con.getInputStream();
        if (in == null) {
            throw new IllegalStateException("Input stream is null for " + url);
        }
        try {
            Scanner scanner = new Scanner(in, "utf-8");
            if (scanner.hasNextLine()) {
                return scanner.nextLine();
            } else {
                throw new IllegalStateException("No content for url " + url);
            }
        } finally {
            in.close();
        }
    }
}
