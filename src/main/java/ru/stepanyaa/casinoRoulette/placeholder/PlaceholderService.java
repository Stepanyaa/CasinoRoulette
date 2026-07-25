package ru.stepanyaa.casinoRoulette.placeholder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;

public final class PlaceholderService {

    public static final String UNKNOWN = null;

    public interface Resolver extends BiFunction<UUID, String, String> {
    }

    private final Map<String, Resolver> exact = new LinkedHashMap<>();

    private final Map<String, Resolver> prefixed = new LinkedHashMap<>();

    public void register(String identifier, Resolver resolver) {
        exact.put(identifier.toLowerCase(java.util.Locale.ROOT), resolver);
    }

    public void registerPrefix(String prefix, Resolver resolver) {
        prefixed.put(prefix.toLowerCase(java.util.Locale.ROOT), resolver);
    }

    public String resolve(UUID uuid, String params) {
        if (params == null || params.isEmpty()) {
            return UNKNOWN;
        }
        String key = params.toLowerCase(java.util.Locale.ROOT);

        Resolver direct = exact.get(key);
        if (direct != null) {
            return safe(direct, uuid, "");
        }
        for (Map.Entry<String, Resolver> entry : prefixed.entrySet()) {
            if (key.startsWith(entry.getKey())) {
                return safe(entry.getValue(), uuid, key.substring(entry.getKey().length()));
            }
        }
        return UNKNOWN;
    }

    private String safe(Resolver resolver, UUID uuid, String args) {
        try {
            String value = resolver.apply(uuid, args);
            return value != null ? value : "";
        } catch (Throwable throwable) {
            return "";
        }
    }

    public boolean isEmpty() {
        return exact.isEmpty() && prefixed.isEmpty();
    }

    public java.util.Set<String> identifiers() {
        return java.util.Collections.unmodifiableSet(exact.keySet());
    }
}
