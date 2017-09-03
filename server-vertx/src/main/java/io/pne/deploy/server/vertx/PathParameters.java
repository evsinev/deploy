package io.pne.deploy.server.vertx;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Deals with URI
 */
public class PathParameters {

    private final List<String> params;
    private final String uri;

    public PathParameters(String aUri) {
        uri = aUri;
        List<String> list = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(aUri, "/");
        while(st.hasMoreTokens()) {
            list.add(st.nextToken());
        }
        params = list;
    }

    public String getLast() {
        return getFromEnd(0);
    }

//    public long getLastLong() {
//        return parseLong(getLast());
//    }

    public String getFromEnd(int aPosition) {
        try {
            return params.get(params.size()-1-aPosition);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to get parameter -"+aPosition+" from "+uri+" "+params);
        }
    }
}