package io.pne.deploy.server.util;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Stream;

public class Strings {

    public static Stream<String> split(String aText, String aDelimeters) {
        List<String> tokens = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(aText, aDelimeters);
        while(st.hasMoreTokens()) {
            tokens.add(st.nextToken());
        }
        return tokens.stream();
    }

}
