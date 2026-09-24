package io.pne.deploy.agent.steps;

import io.pne.deploy.agent.api.command.AgentStep;
import io.pne.deploy.agent.steps.policy.StepPolicy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small helpers shared by the step tests: building steps, a policy rooted at a temporary directory, a log. */
public final class StepTestSupport {

    private StepTestSupport() {
    }

    public static AgentStep step(String aType, String... aKeyValuePairs) {
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < aKeyValuePairs.length; i += 2) {
            params.put(aKeyValuePairs[i], aKeyValuePairs[i + 1]);
        }
        return new AgentStep(aType, params);
    }

    public static StepPolicy policyFor(Path aRoot) {
        return StepPolicy.builder()
                .writeRoots(Collections.singletonList(aRoot.toAbsolutePath().toString()))
                .serviceDirs(Collections.singletonList(aRoot.toAbsolutePath().resolve("service").toString()))
                .fetchHosts(Collections.singletonList("127.0.0.1"))
                .build();
    }

    public static CollectingLog log() {
        return new CollectingLog();
    }

    public static class CollectingLog implements IStepLog {

        private final List<String> lines = new ArrayList<>();

        @Override
        public void log(String aLine) {
            lines.add(aLine);
        }

        public List<String> getLines() {
            return lines;
        }

        public boolean hasLineContaining(String aText) {
            return lines.stream().anyMatch(line -> line.contains(aText));
        }

        @Override
        public String toString() {
            return String.join("\n", lines);
        }
    }
}
