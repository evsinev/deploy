package io.pne.deploy.agent.service.policy;

import io.pne.deploy.agent.steps.policy.StepPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import javax.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads the local policy that says what a step plan may do on this host.
 *
 * <p>The policy lives with the host rather than with the plan, so the host keeps the last word on which sources it
 * downloads from, which directories it writes to and which services it signals. When the file is absent the agent
 * keeps running the legacy commands it always ran, but refuses step plans: a host that has not stated its limits
 * should not be handed new powers by default.
 */
public class PolicyLoader {

    private static final Logger LOG = LoggerFactory.getLogger(PolicyLoader.class);

    private static final Set<String> KNOWN_KEYS = new LinkedHashSet<>(Arrays.asList(
              "allowShell", "shellAllowedPrefix", "fetchHosts", "statusHosts"
            , "writeRoots", "readRoots", "serviceDirs", "serviceControlBinary", "limits"
    ));

    private static final Set<String> KNOWN_LIMITS = new LinkedHashSet<>(Arrays.asList(
              "maxFetchBytes", "maxUnpackBytes", "maxUnpackEntries"
            , "maxStepSeconds", "maxSleepSeconds", "maxPlanSeconds"
    ));

    private PolicyLoader() {
    }

    /** Returns {@code null} when the file does not exist; throws when it exists but cannot be understood. */
    @Nullable
    public static StepPolicy loadOrNull(File aFile) {
        if (aFile == null || !aFile.isFile()) {
            LOG.warn("No agent policy at {}: step plans will be refused", aFile);
            return null;
        }
        try (Reader reader = Files.newBufferedReader(aFile.toPath(), StandardCharsets.UTF_8)) {
            StepPolicy policy = parse(reader);
            LOG.info("Loaded agent policy from {}: {}", aFile, policy);
            return policy;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the agent policy " + aFile + ": " + e.getMessage(), e);
        }
    }

    public static StepPolicy parse(Reader aReader) {
        Yaml    yaml   = new Yaml(new SafeConstructor(new LoaderOptions()));
        Object  loaded = yaml.load(aReader);

        if (loaded == null) {
            return StepPolicy.builder().build();
        }
        if (!(loaded instanceof Map)) {
            throw new IllegalStateException("The agent policy must be a mapping of settings");
        }

        Map<?, ?> map = (Map<?, ?>) loaded;
        checkKnownKeys(map, KNOWN_KEYS, "policy");

        StepPolicy.Builder builder = StepPolicy.builder()
                .allowShell(booleanValue(map, "allowShell", true))
                .shellAllowedPrefix(stringValue(map, "shellAllowedPrefix", ""))
                .fetchHosts(stringList(map, "fetchHosts"))
                .statusHosts(stringList(map, "statusHosts"))
                .writeRoots(stringList(map, "writeRoots"))
                .readRoots(stringList(map, "readRoots"))
                .serviceDirs(stringList(map, "serviceDirs"));

        String controlBinary = stringValue(map, "serviceControlBinary", "");
        if (!controlBinary.isEmpty()) {
            builder.serviceControlBinary(Paths.get(controlBinary));
        }

        Object limits = map.get("limits");
        if (limits != null) {
            if (!(limits instanceof Map)) {
                throw new IllegalStateException("The agent policy 'limits' must be a mapping");
            }
            Map<?, ?> limitsMap = (Map<?, ?>) limits;
            checkKnownKeys(limitsMap, KNOWN_LIMITS, "policy limits");

            builder.maxFetchBytes(longValue(limitsMap, "maxFetchBytes", 1024L * 1024L * 1024L))
                   .maxUnpackBytes(longValue(limitsMap, "maxUnpackBytes", 4L * 1024L * 1024L * 1024L))
                   .maxUnpackEntries((int) longValue(limitsMap, "maxUnpackEntries", 100_000))
                   .maxStepSeconds((int) longValue(limitsMap, "maxStepSeconds", 600))
                   .maxSleepSeconds((int) longValue(limitsMap, "maxSleepSeconds", 120))
                   .maxPlanSeconds((int) longValue(limitsMap, "maxPlanSeconds", 570));
        }

        return builder.build();
    }

    private static void checkKnownKeys(Map<?, ?> aMap, Set<String> aKnown, String aWhat) {
        Set<String> unknown = new TreeSet<>();
        for (Object key : aMap.keySet()) {
            if (!aKnown.contains(String.valueOf(key))) {
                unknown.add(String.valueOf(key));
            }
        }
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("Unknown " + aWhat + " setting(s) " + unknown
                    + "; known settings are " + aKnown);
        }
    }

    private static boolean booleanValue(Map<?, ?> aMap, String aKey, boolean aDefault) {
        Object value = aMap.get(aKey);
        if (value == null) {
            return aDefault;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new IllegalStateException("The agent policy '" + aKey + "' must be true or false, got " + value);
    }

    private static String stringValue(Map<?, ?> aMap, String aKey, String aDefault) {
        Object value = aMap.get(aKey);
        return value == null ? aDefault : String.valueOf(value).trim();
    }

    private static long longValue(Map<?, ?> aMap, String aKey, long aDefault) {
        Object value = aMap.get(aKey);
        if (value == null) {
            return aDefault;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("The agent policy '" + aKey + "' must be a whole number, got " + value);
        }
    }

    private static List<String> stringList(Map<?, ?> aMap, String aKey) {
        Object value = aMap.get(aKey);
        if (value == null) {
            return Collections.emptyList();
        }
        if (!(value instanceof List)) {
            throw new IllegalStateException("The agent policy '" + aKey + "' must be a list");
        }
        List<String> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            String text = String.valueOf(item).trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }
}
