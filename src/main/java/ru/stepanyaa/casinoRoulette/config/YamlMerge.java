package ru.stepanyaa.casinoRoulette.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class YamlMerge {

    private YamlMerge() {
    }

    public static final class Result {

        private final String text;
        private final List<String> addedKeys;

        Result(String text, List<String> addedKeys) {
            this.text = text;
            this.addedKeys = addedKeys;
        }

        public String text() {
            return text;
        }

        public List<String> addedKeys() {
            return addedKeys;
        }

        public boolean changed() {
            return !addedKeys.isEmpty();
        }
    }

    public static Result merge(String userText, String defaultText) {
        if (userText == null || userText.trim().isEmpty()) {

            return new Result(defaultText, new ArrayList<String>());
        }

        Map<String, String> userValues = collectScalars(userText);
        List<String> added = new ArrayList<>();

        String[] defaultLines = defaultText.split("\n", -1);
        StringBuilder out = new StringBuilder();

        List<String> path = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();

        for (String rawLine : defaultLines) {
            String line = stripCarriageReturn(rawLine);
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                out.append(line).append('\n');
                continue;
            }

            if (trimmed.startsWith("- ") || trimmed.equals("-")) {
                out.append(line).append('\n');
                continue;
            }

            int colon = indexOfKeySeparator(trimmed);
            if (colon < 0) {
                out.append(line).append('\n');
                continue;
            }

            int indent = indentOf(line);
            while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
                indents.remove(indents.size() - 1);
                path.remove(path.size() - 1);
            }

            String key = unquote(trimmed.substring(0, colon).trim());
            String value = trimmed.substring(colon + 1).trim();

            path.add(key);
            indents.add(indent);
            String fullKey = join(path);

            if (value.isEmpty()) {

                out.append(line).append('\n');
                continue;
            }

            path.remove(path.size() - 1);
            indents.remove(indents.size() - 1);

            if (userValues.containsKey(fullKey)) {
                String userValue = userValues.get(fullKey);
                out.append(line, 0, line.indexOf(trimmed))
                        .append(trimmed, 0, colon + 1)
                        .append(' ')
                        .append(userValue)
                        .append('\n');
            } else {
                out.append(line).append('\n');
                added.add(fullKey);
            }
        }

        appendUnknownUserKeys(out, userValues, defaultText, added);
        return new Result(out.toString(), added);
    }

    private static void appendUnknownUserKeys(StringBuilder out,
                                              Map<String, String> userValues,
                                              String defaultText,
                                              List<String> added) {
        Map<String, String> defaults = collectScalars(defaultText);
        List<String> orphans = new ArrayList<>();
        for (Map.Entry<String, String> entry : userValues.entrySet()) {
            if (!defaults.containsKey(entry.getKey()) && entry.getKey().indexOf('.') < 0) {
                orphans.add(entry.getKey());
            }
        }
        if (orphans.isEmpty()) {
            return;
        }
        out.append('\n')
                .append("# Options kept from your previous configuration.\n")
                .append("# They are no longer part of the shipped defaults.\n");
        for (String key : orphans) {
            out.append(key).append(": ").append(userValues.get(key)).append('\n');
        }
    }

    public static Map<String, String> collectScalars(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        if (text == null) {
            return values;
        }

        List<String> path = new ArrayList<>();
        List<Integer> indents = new ArrayList<>();

        for (String rawLine : text.split("\n", -1)) {
            String line = stripCarriageReturn(rawLine);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("- ")) {
                continue;
            }

            int colon = indexOfKeySeparator(trimmed);
            if (colon < 0) {
                continue;
            }

            int indent = indentOf(line);
            while (!indents.isEmpty() && indents.get(indents.size() - 1) >= indent) {
                indents.remove(indents.size() - 1);
                path.remove(path.size() - 1);
            }

            String key = unquote(trimmed.substring(0, colon).trim());
            String value = trimmed.substring(colon + 1).trim();

            path.add(key);
            indents.add(indent);

            if (!value.isEmpty()) {
                values.put(join(path), value);
                path.remove(path.size() - 1);
                indents.remove(indents.size() - 1);
            }
        }
        return values;
    }

    private static int indexOfKeySeparator(String trimmed) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == ':' && !inSingle && !inDouble) {
                boolean endOfLine = i == trimmed.length() - 1;
                if (endOfLine || trimmed.charAt(i + 1) == ' ') {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    private static String unquote(String key) {
        if (key.length() >= 2
                && ((key.startsWith("'") && key.endsWith("'"))
                || (key.startsWith("\"") && key.endsWith("\"")))) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }

    private static String join(List<String> path) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(path.get(i));
        }
        return sb.toString();
    }

    private static String stripCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
