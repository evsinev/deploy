package io.pne.deploy.server.service.impl.alias;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Values that describe where the aliases are deployed rather than what they do.
 *
 * <p>The same recipe serves every location; what differs is the artifact source, and naming it once here keeps
 * that difference out of every alias file.
 */
public class SiteVars {

    /** Files whose name starts with this are settings, not aliases. */
    public static final String RESERVED_PREFIX = "_";

    public static final String FILE_NAME = "_site.yml";

    public static final String SCOPE = "site";

    private static final Logger LOG = LoggerFactory.getLogger(SiteVars.class);

    private final Map<String, String> values;

    public SiteVars(Map<String, String> aValues) {
        values = new LinkedHashMap<>(aValues);
    }

    public static SiteVars load(File aDirectory) {
        File file = new File(aDirectory, FILE_NAME);
        if (!file.isFile()) {
            return new SiteVars(Collections.emptyMap());
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            Holder holder = new Yaml().loadAs(reader, Holder.class);
            Map<String, String> values = new LinkedHashMap<>();
            if (holder != null && holder.vars != null) {
                holder.vars.forEach((name, value) -> values.put(name, String.valueOf(value)));
            }
            return new SiteVars(values);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Cannot read " + file + ": " + e.getMessage(), e);
        }
    }

    public Map<String, String> getValues() {
        return Collections.unmodifiableMap(values);
    }

    @Override
    public String toString() {
        return values.toString();
    }

    /** Shape of the file, for the parser. */
    public static class Holder {
        public Map<String, Object> vars;
    }
}
