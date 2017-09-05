package io.pne.deploy.tests;

import java.io.*;

public class ShowAnsibleDebug {

    public static void main(String[] args) throws IOException {
        try (LineNumberReader in = new LineNumberReader(new FileReader(new File(args[0])))) {
            String line;
            while ( (line = in.readLine()) != null) {
                line = line.replace("\\r\\n", "\n");
                System.out.println(line);
            }
        }

    }
}
