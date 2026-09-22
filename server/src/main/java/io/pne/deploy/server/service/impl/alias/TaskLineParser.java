package io.pne.deploy.server.service.impl.alias;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns the words of a task line into the values an alias declared.
 *
 * <p>A word can be claimed by position or by a keyword before it, which is how a line naming several versions
 * stays readable. Whatever is left over is reported rather than ignored: a value in the wrong place used to pass
 * silently and deploy something other than what was asked for.
 */
public class TaskLineParser {

    private TaskLineParser() {
    }

    public static Parsed parse(String aAliasName, List<String> aWords, List<AliasParam> aParams) {
        List<Word>           words     = new ArrayList<>();
        for (int i = 0; i < aWords.size(); i++) {
            words.add(new Word(aWords.get(i), i + 1));
        }

        Map<String, String>  values    = new LinkedHashMap<>();
        Map<String, Integer> lineIndex = new LinkedHashMap<>();

        for (AliasParam param : aParams) {
            if (isKeyed(param)) {
                Word word = takeKeyed(aAliasName, words, param);
                if (word != null) {
                    values.put(param.name, word.text);
                    lineIndex.put(param.name, word.index);
                } else {
                    values.put(param.name, null);
                }
            }
        }

        assignPositions(aAliasName, aParams);

        for (AliasParam param : aParams) {
            if (isKeyed(param)) {
                continue;
            }
            int at = param.position;
            if (at <= words.size()) {
                Word word = words.get(at - 1);
                values.put(param.name, word.text);
                lineIndex.put(param.name, word.index);
            } else {
                values.put(param.name, null);
            }
        }

        int claimed = aParams.stream()
                .filter(param -> !isKeyed(param))
                .mapToInt(param -> param.position)
                .max()
                .orElse(0);
        if (words.size() > claimed) {
            List<String> extra = words.subList(claimed, words.size()).stream()
                    .map(word -> word.text)
                    .collect(java.util.stream.Collectors.toList());
            throw new IllegalArgumentException("Too many values for alias " + aAliasName
                    + ": " + extra + " is not expected. " + usage(aAliasName, aParams));
        }

        for (AliasParam param : aParams) {
            values.put(param.name, check(aAliasName, param, values.get(param.name), aParams));
        }
        return new Parsed(values, lineIndex);
    }

    private static boolean isKeyed(AliasParam aParam) {
        return aParam.key != null && !aParam.key.isEmpty();
    }

    /** The values of a task line, and where each one sat on it. */
    public static class Parsed {

        private final Map<String, String>  values;
        private final Map<String, Integer> lineIndex;

        Parsed(Map<String, String> aValues, Map<String, Integer> aLineIndex) {
            values    = aValues;
            lineIndex = aLineIndex;
        }

        public Map<String, String> getValues() {
            return values;
        }

        /** Which word of the task line a value came from, counting from 1, or 0 when it was not on the line. */
        public int getLineIndex(String aName) {
            return lineIndex.getOrDefault(aName, 0);
        }
    }

    private static class Word {

        private final String text;
        private final int    index;

        Word(String aText, int aIndex) {
            text  = aText;
            index = aIndex;
        }
    }

    /** A line showing how the alias is called, built from what it declared. */
    public static String usage(String aAliasName, List<AliasParam> aParams) {
        StringBuilder usage = new StringBuilder("Usage: ").append(aAliasName);
        for (AliasParam param : aParams) {
            if (isKeyed(param)) {
                usage.append(' ').append(param.key);
            }
            usage.append(' ');
            usage.append(param.required ? "<" + param.name + ">" : "[" + param.name + "]");
        }
        return usage.toString();
    }

    private static Word takeKeyed(String aAliasName, List<Word> aWords, AliasParam aParam) {
        int at = -1;
        for (int i = 0; i < aWords.size(); i++) {
            if (aParam.key.equals(aWords.get(i).text)) {
                at = i;
                break;
            }
        }
        if (at < 0) {
            return null;
        }
        if (at + 1 >= aWords.size()) {
            throw new IllegalArgumentException("Alias " + aAliasName + ": nothing follows '" + aParam.key
                    + "', which should be followed by the " + aParam.name);
        }
        Word value = aWords.get(at + 1);
        aWords.remove(at + 1);
        aWords.remove(at);
        return value;
    }

    /** Gives every positional value an index: the one it asked for, or the next one still free. */
    private static void assignPositions(String aAliasName, List<AliasParam> aParams) {
        List<Integer> taken = new ArrayList<>();
        for (AliasParam param : aParams) {
            if (!isKeyed(param) && param.position != null) {
                if (param.position < 1) {
                    throw new IllegalArgumentException("Alias " + aAliasName + ", parameter " + param.name
                            + ": position must be 1 or more, got " + param.position);
                }
                if (taken.contains(param.position)) {
                    throw new IllegalArgumentException("Alias " + aAliasName + ": position " + param.position
                            + " is claimed by more than one parameter");
                }
                taken.add(param.position);
            }
        }

        int next = 1;
        for (AliasParam param : aParams) {
            if (isKeyed(param)) {
                continue;
            }
            if (param.position == null) {
                while (taken.contains(next)) {
                    next++;
                }
                param.position = next;
                taken.add(next);
            }
        }
    }

    private static String check(String aAliasName, AliasParam aParam, String aValue, List<AliasParam> aParams) {
        String value = aValue;
        if (value == null || value.isEmpty()) {
            if (aParam.required) {
                throw new IllegalArgumentException("Alias " + aAliasName + " needs a value for '" + aParam.name
                        + "'" + describe(aParam) + ". " + usage(aAliasName, aParams));
            }
            value = aParam.defaultValue;
        }
        if (value == null) {
            return null;
        }

        ParamType type    = ParamType.of(aParam.type);
        String    pattern = aParam.pattern != null ? aParam.pattern : type.getPattern();

        if (type == ParamType.ENUM) {
            if (aParam.values == null || !aParam.values.contains(value)) {
                throw new IllegalArgumentException("Alias " + aAliasName + ": '" + aParam.name + "' must be one of "
                        + aParam.values + ", got '" + value + "'");
            }
            return value;
        }
        if (pattern == null) {
            throw new IllegalArgumentException("Alias " + aAliasName + ": '" + aParam.name
                    + "' is a string and needs a pattern");
        }
        if (!Pattern.compile(pattern).matcher(value).matches()) {
            throw new IllegalArgumentException("Alias " + aAliasName + ": '" + aParam.name + "' is '" + value
                    + "', which is not a valid " + type.name().toLowerCase(Locale.ROOT)
                    + " (expected " + pattern + ")");
        }
        return value;
    }

    private static String describe(AliasParam aParam) {
        if (aParam.description != null && !aParam.description.isEmpty()) {
            return " (" + aParam.description + ")";
        }
        if (isKeyed(aParam)) {
            return " (after the word '" + aParam.key + "')";
        }
        return " (word " + aParam.position + " of the line)";
    }
}
