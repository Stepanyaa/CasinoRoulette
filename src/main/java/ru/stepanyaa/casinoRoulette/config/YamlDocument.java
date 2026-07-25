package ru.stepanyaa.casinoRoulette.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class YamlDocument {

    private final Map<String, Object> root;

    private YamlDocument(Map<String, Object> root) {
        this.root = root;
    }

    public static YamlDocument empty() {
        return new YamlDocument(new LinkedHashMap<>());
    }

    public static YamlDocument parse(String text) {
        return new YamlDocument(new Parser(text).parse());
    }

    public static YamlDocument load(File file) {
        try {
            if (file == null || !file.isFile()) {
                return empty();
            }
            return parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException failure) {
            return empty();
        }
    }

    private Object resolve(String path) {
        if (path == null || path.isEmpty()) {
            return root;
        }
        Object current = root;
        int from = 0;
        while (from <= path.length()) {
            int dot = path.indexOf('.', from);
            String part = dot < 0 ? path.substring(from) : path.substring(from, dot);
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(part);
            if (current == null) {
                return null;
            }
            if (dot < 0) {
                return current;
            }
            from = dot + 1;
        }
        return current;
    }

    public boolean contains(String path) {
        return resolve(path) != null;
    }

    public boolean isSection(String path) {
        return resolve(path) instanceof Map;
    }

    public String getString(String path, String def) {
        Object value = resolve(path);
        return value instanceof String ? (String) value : def;
    }

    public int getInt(String path, int def) {
        Object value = resolve(path);
        if (!(value instanceof String)) {
            return def;
        }
        try {
            String text = ((String) value).trim();

            return text.indexOf('.') >= 0
                    ? (int) Double.parseDouble(text)
                    : Integer.parseInt(text);
        } catch (NumberFormatException notANumber) {
            return def;
        }
    }

    public long getLong(String path, long def) {
        Object value = resolve(path);
        if (!(value instanceof String)) {
            return def;
        }
        try {
            return Long.parseLong(((String) value).trim());
        } catch (NumberFormatException notANumber) {
            return def;
        }
    }

    public double getDouble(String path, double def) {
        Object value = resolve(path);
        if (!(value instanceof String)) {
            return def;
        }
        try {
            return Double.parseDouble(((String) value).trim());
        } catch (NumberFormatException notANumber) {
            return def;
        }
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = resolve(path);
        if (!(value instanceof String)) {
            return def;
        }
        String text = ((String) value).trim();
        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes") || text.equalsIgnoreCase("on")) {
            return true;
        }
        if (text.equalsIgnoreCase("false") || text.equalsIgnoreCase("no") || text.equalsIgnoreCase("off")) {
            return false;
        }
        return def;
    }

    public List<String> getStringList(String path) {
        Object value = resolve(path);
        if (value instanceof List) {
            List<String> copy = new ArrayList<>();
            for (Object element : (List<?>) value) {
                copy.add(String.valueOf(element));
            }
            return copy;
        }
        if (value instanceof String) {
            String single = (String) value;
            return single.isEmpty() ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(single));
        }
        return new ArrayList<>();
    }

    public Set<String> keys(String path) {
        Object value = resolve(path);
        if (!(value instanceof Map)) {
            return Collections.emptySet();
        }
        Map<String, Object> section = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            section.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return section.keySet();
    }

    private static final class Parser {

        private final String[] lines;
        private int index;

        Parser(String text) {
            this.lines = (text == null ? "" : text).replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        }

        Map<String, Object> parse() {
            Map<String, Object> result = new LinkedHashMap<>();
            parseInto(result, 0);
            return result;
        }

        private void parseInto(Map<String, Object> target, int indent) {
            while (index < lines.length) {
                String line = lines[index];
                if (isSkippable(line)) {
                    index++;
                    continue;
                }
                int lineIndent = indentOf(line);
                if (lineIndent < indent) {
                    return;
                }
                String trimmed = line.trim();
                if (trimmed.startsWith("- ") || trimmed.equals("-")) {

                    index++;
                    continue;
                }
                if (lineIndent > indent) {

                    index++;
                    continue;
                }

                int colon = colonIndex(trimmed);
                if (colon < 0) {
                    index++;
                    continue;
                }

                String key = unquote(trimmed.substring(0, colon).trim());
                String rest = trimmed.substring(colon + 1).trim();
                index++;

                if (!rest.isEmpty()) {
                    if (rest.equals("[]")) {
                        target.put(key, new ArrayList<String>());
                    } else {
                        target.put(key, scalar(rest));
                    }
                    continue;
                }

                int nextIndent = peekIndent();
                if (nextIndent < 0) {
                    target.put(key, "");
                    continue;
                }
                if (isListAt(nextIndent, indent)) {
                    target.put(key, parseList(nextIndent));
                } else if (nextIndent > indent) {
                    Map<String, Object> child = new LinkedHashMap<>();
                    parseInto(child, nextIndent);
                    target.put(key, child);
                } else {
                    target.put(key, "");
                }
            }
        }

        private boolean isListAt(int nextIndent, int keyIndent) {
            if (nextIndent < keyIndent) {
                return false;
            }
            String next = peekLine();
            return next != null && (next.trim().startsWith("- ") || next.trim().equals("-"));
        }

        private List<String> parseList(int listIndent) {
            List<String> items = new ArrayList<>();
            while (index < lines.length) {
                String line = lines[index];
                if (isSkippable(line)) {
                    index++;
                    continue;
                }
                if (indentOf(line) < listIndent) {
                    break;
                }
                String trimmed = line.trim();
                if (!trimmed.startsWith("- ") && !trimmed.equals("-")) {
                    break;
                }
                items.add(trimmed.equals("-") ? "" : scalar(trimmed.substring(2).trim()));
                index++;
            }
            return items;
        }

        private String peekLine() {
            for (int i = index; i < lines.length; i++) {
                if (!isSkippable(lines[i])) {
                    return lines[i];
                }
            }
            return null;
        }

        private int peekIndent() {
            String line = peekLine();
            return line == null ? -1 : indentOf(line);
        }

        private static boolean isSkippable(String line) {
            String trimmed = line.trim();
            return trimmed.isEmpty() || trimmed.charAt(0) == '#' || trimmed.equals("---");
        }

        private static int indentOf(String line) {
            int i = 0;
            while (i < line.length() && line.charAt(i) == ' ') {
                i++;
            }
            return i;
        }

        private static int colonIndex(String trimmed) {
            char quote = 0;
            for (int i = 0; i < trimmed.length(); i++) {
                char c = trimmed.charAt(i);
                if (quote != 0) {
                    if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '\'' || c == '"') {
                    quote = c;
                } else if (c == ':') {

                    if (i + 1 >= trimmed.length() || trimmed.charAt(i + 1) == ' ') {
                        return i;
                    }
                }
            }
            return -1;
        }

        private static String scalar(String raw) {
            String value = raw;
            if (!value.isEmpty() && (value.charAt(0) == '\'' || value.charAt(0) == '"')) {
                return unquote(value);
            }
            int comment = value.indexOf(" #");
            if (comment >= 0) {
                value = value.substring(0, comment);
            }
            return value.trim();
        }

        private static String unquote(String raw) {
            String value = raw.trim();
            if (value.length() < 2) {
                return value;
            }
            char first = value.charAt(0);
            if (first != '\'' && first != '"') {
                return value;
            }
            int closing = -1;
            for (int i = 1; i < value.length(); i++) {
                if (value.charAt(i) == first) {

                    if (first == '\'' && i + 1 < value.length() && value.charAt(i + 1) == '\'') {
                        i++;
                        continue;
                    }
                    closing = i;
                    break;
                }
            }
            if (closing < 0) {
                return value;
            }
            String inner = value.substring(1, closing);
            if (first == '\'') {
                return inner.replace("''", "'");
            }
            return inner.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
        }
    }
}
