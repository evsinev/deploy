package io.pne.deploy.server.service.impl.alias.recipe;

import org.yaml.snakeyaml.Yaml;

import javax.annotation.Nonnull;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Reads recipes from disk on every use, so a correction takes effect without restarting anything. */
public class RecipeLoader {

    private static final String EXTENSION = ".yml";

    private final File directory;
    private final Yaml yaml = new Yaml();

    public RecipeLoader(File aDirectory) {
        directory = aDirectory;
    }

    public File getDirectory() {
        return directory;
    }

    @Nonnull
    public RecipeDescription load(String aName) throws IOException {
        File file = new File(directory, aName + EXTENSION);
        if (!file.isFile()) {
            throw new IllegalArgumentException("Recipe '" + aName + "' not found in " + directory
                    + ". Available recipes are: " + getAvailableRecipes());
        }

        RecipeDescription recipe;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            recipe = yaml.loadAs(reader, RecipeDescription.class);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Recipe '" + aName + "' cannot be read: " + e.getMessage(), e);
        }

        if (recipe == null) {
            throw new IllegalArgumentException("Recipe '" + aName + "' is empty");
        }
        if (recipe.steps == null || recipe.steps.isEmpty()) {
            throw new IllegalArgumentException("Recipe '" + aName + "' has no steps");
        }
        return recipe;
    }

    public List<String> getAvailableRecipes() {
        String[] names = directory.list((dir, name) -> name.endsWith(EXTENSION));
        if (names == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(names)
                .map(name -> name.substring(0, name.length() - EXTENSION.length()))
                .sorted()
                .collect(Collectors.toList());
    }
}
