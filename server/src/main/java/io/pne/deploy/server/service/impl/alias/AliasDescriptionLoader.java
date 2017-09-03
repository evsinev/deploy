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

public class AliasDescriptionLoader {

    private final File aliasDir;
    private final Yaml yaml = new Yaml();

    public AliasDescriptionLoader(File aliasDir) {
        this.aliasDir = aliasDir;
    }

    @Nonnull
    public AliasDescription loadAlias(AliasParameters aliasParameters) throws IOException {
        File file = new File(aliasDir, aliasParameters.name + ".yml");
        if (!file.exists()) {
            throw new IllegalArgumentException("Alias " + aliasParameters.name + " not found. Available aliases are: "
                    + getAvailableAliases()
            );
        }

        String yamlText = loadYaml(file);
        String withParameters = processParameters(yamlText, aliasParameters.parameters);

        AliasDescription description = yaml.loadAs(withParameters, AliasDescription.class);
        if(description == null) {
            throw new IllegalStateException("Cannot load alias description from file " + file.getName()
                    +"\nBEGIN FILE\n"
                    + withParameters
                    +"\nEND FILE"
            );
        }
        return description;
    }

    private List<String> getAvailableAliases() {
        return Arrays.stream(notNull(aliasDir.list((dir, name) -> name.endsWith(".yml"))))
                .map(filename -> filename.replace(".yml", ""))
                .collect(Collectors.toList());
    }

    private static String[] notNull(String[] array) {
        return array != null ? array : new String[]{};
    }

    private String processParameters(String aText, List<String> aParameters) {
        String ret = aText;
        for (int i = 0; i < aParameters.size(); i++) {
            int index = i + 1;
            ret = ret.replace("$" + index, aParameters.get(i));
        }
        return ret;
    }

    private String loadYaml(File aFile) throws IOException {
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
