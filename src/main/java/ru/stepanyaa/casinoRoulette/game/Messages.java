package ru.stepanyaa.casinoRoulette.game;

import ru.stepanyaa.casinoRoulette.config.YamlDocument;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Messages {

    private volatile YamlDocument lang;

    public Messages(YamlDocument lang) {
        this.lang = lang == null ? YamlDocument.empty() : lang;
    }

    public void update(YamlDocument reloaded) {
        if (reloaded != null) {
            this.lang = reloaded;
        }
    }

    public String get(String path, String def, Object... placeholders) {
        return apply(lang.getString(path, def), placeholders);
    }

    public List<String> getList(String path, List<String> def, Object... placeholders) {
        List<String> lines = lang.getStringList(path);
        if (lines.isEmpty()) {
            lines = def == null ? new ArrayList<>() : new ArrayList<>(def);
        }
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(apply(line, placeholders));
        }
        return result;
    }

    public List<String> getList(String path, String... def) {
        return getList(path, Arrays.asList(def));
    }

    public String prefix() {
        return get("messages.prefix", "&7[&6Casino&7] ");
    }

    public String chat(String path, String def, Object... placeholders) {
        return prefix() + get(path, def, placeholders);
    }

    private static String apply(String template, Object... placeholders) {
        if (template == null) {
            return "";
        }
        String text = template;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            text = text.replace("%" + placeholders[i] + "%", String.valueOf(placeholders[i + 1]));
        }
        return text;
    }
}
