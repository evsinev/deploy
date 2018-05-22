package io.pne.deploy.client.redmine.remote.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;

public class RedmineRemoveConfigBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(RedmineRemoveConfigBuilder.class);

    public IRedmineRemoteConfig build() {
        return ImmutableIRedmineRemoteConfig.builder()
                .url                        ( getRequired          ("REDMINE_URL"                                  ))
                .apiAccessKey               ( getRequired          ("REDMINE_API_ACCESS_KEY"                       ))
                .putAllIssuesQueryParameters( createParametersMap  ("REDMINE_QUERY_"                               ))
                .statusAcceptedId           ( getInt               ("REDMINE_STATUS_ACCEPT_ID"      , 1   )) // new
                .statusProcessingId         ( getInt               ("REDMINE_STATUS_PROCESSING_ID"  , 2   )) // in progress
                .statusDoneId               ( getInt               ("REDMINE_STATUS_DONE_ID"        , 3   )) // resolved
                .statusFailedId             ( getInt               ("REDMINE_STATUS_FAILED_ID"      , 6   )) // rejected
                .connectTimeoutSeconds      ( getInt               ("REDMINE_CONNECT_TIMEOUT"       , 120 )) // 2 minutes
                .readTimeoutSeconds         ( getInt               ("REDMINE_READ_TIMEOUT"          , 120 )) // 2 minutes
                .redmineCallbackUrl         ( getRequired          ("REDMINE_CALLBACK_URI"                         ))
                .issueValidationScript      ( getRequired          ("ISSUE_VALIDATION_SCRIPT"                      ))
                .build()
                ;
    }

    private int getRequiredInt(String aName) {
        return Integer.parseInt(getRequired(aName));
    }

    private int getInt(String aName, int aDefault) {
        return Integer.parseInt(get(aName, ""+aDefault));
    }

    private Map<String, ? extends String> createParametersMap(String aPrefix) {
        Map<String, String> map = new TreeMap<>();
        for(int i=0; i<100; i++) {
            String name = aPrefix + i;
            String value = get(name);
            if(value == null) {
                break;
            }
            StringTokenizer st = new StringTokenizer(value, "= ");
            map.put(st.nextToken(), st.nextToken());
        }

        LOG.info("Map is {}", map);
        return map;
    }

    private String getRequired(String aName) {
        String value = get(aName);
        if(value == null){
            throw new IllegalArgumentException("No env or property " + aName);
        }
        return value;
    }


    private String get(String aName) {
        return get(aName, null);
    }

    private String get(String aName, String aDefault) {
        String value = System.getenv(aName);
        if(value == null) {
            value = System.getProperty(aName, aDefault);
        }
        LOG.info("    {} = {}", aName, filterSensitiveData(aName, value));
        return value;
    }

    private Object filterSensitiveData(String aName, String aValue) {
        if(aName.contains("ACCESS") || aName.contains("PASSW")) {
            return "***";
        }
        return aValue;
    }
}
