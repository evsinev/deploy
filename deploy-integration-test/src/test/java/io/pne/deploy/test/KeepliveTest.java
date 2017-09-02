package io.pne.deploy.test;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

public class KeepliveTest {

    public static void main(String[] args) throws MalformedURLException {
        for (int i=0; i<1000; i++) {
            getUrl();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    private static void getUrl() throws MalformedURLException {
        URL url = new URL("https://mclr.pne.io/version/app");

        try {
            URLConnection connection = url.openConnection();
            InputStream in = connection.getInputStream();
            try {
                byte[] buf = new byte[2048];

                int count;
                while ( (count = in.read(buf)) >= 0) {
                    System.out.println(new String(buf, 0, count));
                }
            } finally {
                in.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
