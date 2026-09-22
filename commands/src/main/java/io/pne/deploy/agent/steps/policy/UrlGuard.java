package io.pne.deploy.agent.steps.policy;

import io.pne.deploy.agent.steps.StepValidationException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Checks that a URL from a plan is plain HTTP(S) and points at a host the policy allows. */
public class UrlGuard {

    private UrlGuard() {
    }

    public static URI check(HostAllowList aAllowed, String aStepType, String aParam, String aUrl) throws StepValidationException {
        URI uri;
        try {
            uri = new URI(aUrl.trim());
        } catch (URISyntaxException e) {
            throw new StepValidationException("step '" + aStepType + "', parameter '" + aParam
                    + "' is not a valid URL: " + e.getMessage());
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new StepValidationException("step '" + aStepType + "', parameter '" + aParam
                    + "' must be an http or https URL, got '" + aUrl + "'");
        }
        if (uri.getHost() == null) {
            throw new StepValidationException("step '" + aStepType + "', parameter '" + aParam
                    + "' has no host, got '" + aUrl + "'");
        }
        if (aAllowed.isEmpty()) {
            throw new StepValidationException("step '" + aStepType + "', parameter '" + aParam
                    + "': no hosts are allowed by the agent policy, so " + uri.getHost() + " cannot be reached");
        }
        if (!aAllowed.allows(uri.getHost(), uri.getPort())) {
            throw new StepValidationException("step '" + aStepType + "', parameter '" + aParam
                    + "': host " + uri.getHost() + " is not allowed by the agent policy " + aAllowed);
        }
        return uri;
    }
}
