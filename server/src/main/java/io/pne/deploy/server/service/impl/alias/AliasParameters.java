package io.pne.deploy.server.service.impl.alias;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class AliasParameters {

    public final String name;
    public final List<String> parameters;

    public AliasParameters(String aLine) {
        StringTokenizer st = new StringTokenizer(aLine, " ");
        name = st.nextToken();
        parameters = new ArrayList<>();
        while(st.hasMoreTokens()) {
            parameters.add(st.nextToken());
        }
    }
}
