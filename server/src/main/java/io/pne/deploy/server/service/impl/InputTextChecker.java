package io.pne.deploy.server.service.impl;

import java.util.regex.Pattern;

public class InputTextChecker {

    private final Pattern aliasPattern = Pattern.compile("^[A-Za-z0-9\\s.\\-_]+$");

    public void checkAlias(String aAlias) {
        if(!aliasPattern.matcher(aAlias).matches()) {
            throw new IllegalArgumentException("Alias contains illegal characters");
        }
    }
}
