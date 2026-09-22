package io.pne.deploy.server.service.impl.alias;

import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.List;
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

    private String processParameters(String aText, List<String> aParameters, int aIssueId) {
        String ret = aText;
        for (int i = 0; i < aParameters.size(); i++) {
            int index = i + 1;
            ret = ret.replace("$" + index, aParameters.get(i));
        }

        ret = ret.replace("$ISSUE_ID", String.valueOf(aIssueId));

        return ret;
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
