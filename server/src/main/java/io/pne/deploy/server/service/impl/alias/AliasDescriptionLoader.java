package io.pne.deploy.server.service.impl.alias;

import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Reads an alias from disk.
 *
 * <p>Two forms are understood, so that files can be moved over one at a time. A file that declares its values
 * under {@code params:} is parsed first and the values are put into the parsed result; an older file without
 * that declaration keeps the previous behaviour, where the numbered placeholders are replaced in the text before
 * it is parsed. The newer form is what makes a file checkable: a value can only land where a value was expected.
 */
public class AliasDescriptionLoader {

    /** Longest first, so the tenth value is not read as the first one followed by a zero. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$(ISSUE_ID|[0-9]+)");

    private final File aliasDir;
    private final Yaml yaml = new Yaml();

    public AliasDescriptionLoader(File aliasDir) {
        this.aliasDir = aliasDir;
    }

    public File getAliasDir() {
        return aliasDir;
    }

    @Nonnull
    public AliasDescription loadAlias(AliasParameters aliasParameters, int aIssueId) throws IOException {
        File file = aliasFile(aliasParameters.name);
        if (!file.exists()) {
            throw new IllegalArgumentException("Alias " + aliasParameters.name + " not found. Available aliases are: "
                    + getAvailableAliases()
            );
        }

        String yamlText = loadYaml(file);

        // Which form the file is in is decided by reading it, not by looking for a word in its text: a file that
        // declares its values must never be put through text replacement, whichever way it happens to be written.
        AliasDescription asWritten = tryParse(yamlText);
        if (asWritten != null && asWritten.params != null) {
            return asWritten;
        }

        String withParameters = processParameters(yamlText, aliasParameters.parameters, aIssueId);
        return parse(file, withParameters);
    }

    /** Parses the file as it stands, or returns {@code null} when it only makes sense after text replacement. */
    private AliasDescription tryParse(String aYamlText) {
        try {
            return yaml.loadAs(aYamlText, AliasDescription.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public File aliasFile(String aName) {
        return new File(aliasDir, aName + ".yml");
    }

    @Nonnull
    private AliasDescription parse(File aFile, String aYamlText) {
        AliasDescription description;
        try {
            description = yaml.loadAs(aYamlText, AliasDescription.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Alias " + aFile.getName() + " cannot be read: " + e.getMessage(), e);
        }
        if (description == null) {
            throw new IllegalStateException("Cannot load alias description from file " + aFile.getName()
                    +"\nBEGIN FILE\n"
                    + aYamlText
                    +"\nEND FILE"
            );
        }
        return description;
    }

    public List<String> getAvailableAliases() {
        return Arrays.stream(notNull(aliasDir.list((dir, name) -> name.endsWith(".yml"))))
                .filter(filename -> !filename.startsWith(SiteVars.RESERVED_PREFIX))
                .map(filename -> filename.replace(".yml", ""))
                .sorted()
                .collect(Collectors.toList());
    }

    private static String[] notNull(String[] array) {
        return array != null ? array : new String[]{};
    }

    /**
     * The older way of filling in values: replacing numbered placeholders in the text of the file.
     *
     * <p>Done in one pass, so a value that happens to contain something looking like a placeholder is left
     * alone, and longest-first, so {@code $10} is not read as {@code $1} followed by a zero. For the aliases in
     * use today, which never go past nine values, this produces exactly what it always did.
     */
    private String processParameters(String aText, List<String> aParameters, int aIssueId) {
        Matcher       matcher = PLACEHOLDER.matcher(aText);
        StringBuilder result  = new StringBuilder();

        while (matcher.find()) {
            String        token       = matcher.group(1);
            String        replacement = matcher.group();
            if ("ISSUE_ID".equals(token)) {
                replacement = String.valueOf(aIssueId);
            } else {
                int index = Integer.parseInt(token);
                if (index >= 1 && index <= aParameters.size()) {
                    replacement = aParameters.get(index - 1);
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public String loadYaml(File aFile) throws IOException {
        try (LineNumberReader reader = new LineNumberReader(new FileReader(aFile))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        }

    }
}
