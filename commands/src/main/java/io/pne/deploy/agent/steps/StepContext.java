package io.pne.deploy.agent.steps;

import io.pne.deploy.agent.steps.policy.StepPolicy;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

/** Everything a step needs while it runs: the policy, the log, the variables earlier steps produced, an HTTP client. */
public class StepContext {

    private final StepPolicy          policy;
    private final IStepLog            log;
    private final Map<String, String> variables = new LinkedHashMap<>();

    private HttpClient httpClient;

    public StepContext(StepPolicy aPolicy, IStepLog aLog) {
        policy = aPolicy;
        log    = aLog;
    }

    public StepPolicy getPolicy() {
        return policy;
    }

    public void log(String aLine) {
        log.log(aLine);
    }

    public void setVariable(String aName, String aValue) {
        variables.put(aName, aValue);
    }

    public Map<String, String> getVariables() {
        return variables;
    }

    /** Replaces {@code ${name}} with what an earlier step produced. An unknown name is an error, never an empty string. */
    public String expand(String aValue) throws StepExecutionException {
        if (aValue == null || aValue.indexOf("${") < 0) {
            return aValue;
        }
        Matcher matcher = StepPlanScope.variablePattern().matcher(aValue);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name  = matcher.group(1);
            String value = variables.get(name);
            if (value == null) {
                throw new StepExecutionException("Variable ${" + name + "} is not set; variables set so far: "
                        + variables.keySet());
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
        }
        return httpClient;
    }
}
