package ru.stepanyaa.casinoRoulette.platform.impl.sponge;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class SpongeReflection {

    private static final Map<String, Class<?>> CLASSES = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();

    private SpongeReflection() {
    }

    static Class<?> type(String name) throws ClassNotFoundException {
        Class<?> cached = CLASSES.get(name);
        if (cached != null) {
            return cached;
        }
        Class<?> resolved = Class.forName(name);
        CLASSES.put(name, resolved);
        return resolved;
    }

    static boolean available() {
        try {
            type("org.spongepowered.api.Sponge");
            return true;
        } catch (Throwable notSponge) {
            return false;
        }
    }

    static Method method(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        StringBuilder key = new StringBuilder(owner.getName()).append('#').append(name);
        for (Class<?> parameter : parameters) {
            key.append(':').append(parameter.getName());
        }
        Method cached = METHODS.get(key.toString());
        if (cached != null) {
            return cached;
        }
        Method resolved = accessible(owner, name, parameters);
        METHODS.put(key.toString(), resolved);
        return resolved;
    }
    private static Method accessible(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        Method direct = owner.getMethod(name, parameters);
        if (usable(direct)) {
            return direct;
        }
        for (Class<?> candidate : hierarchy(owner)) {
            if (candidate == direct.getDeclaringClass()) {
                continue;
            }
            try {
                Method inherited = candidate.getMethod(name, parameters);
                if (usable(inherited)) {
                    return inherited;
                }
            } catch (NoSuchMethodException notDeclaredHere) {
            }
        }
        try {
            direct.setAccessible(true);
        } catch (Throwable inaccessible) {
        }
        return direct;
    }

    private static boolean usable(Method method) {
        Class<?> declaring = method.getDeclaringClass();
        if (!java.lang.reflect.Modifier.isPublic(declaring.getModifiers())) {
            return false;
        }
        if (!declaring.getModule().isExported(declaring.getPackageName())) {
            return false;
        }
        try {
            method.setAccessible(true);
        } catch (Throwable alreadyPublic) {
        }
        return true;
    }

    static java.util.List<Class<?>> hierarchy(Class<?> type) {
        java.util.List<Class<?>> found = new java.util.ArrayList<>();
        java.util.Deque<Class<?>> pending = new java.util.ArrayDeque<>();
        pending.add(type);
        while (!pending.isEmpty()) {
            Class<?> current = pending.poll();
            if (current == null || found.contains(current)) {
                continue;
            }
            found.add(current);
            if (current.getSuperclass() != null) {
                pending.add(current.getSuperclass());
            }
            pending.addAll(java.util.Arrays.asList(current.getInterfaces()));
        }
        return found;
    }

    static String typeNames(Object value) {
        if (value == null) {
            return "";
        }
        StringBuilder names = new StringBuilder();
        for (Class<?> type : hierarchy(value.getClass())) {
            names.append(type.getName()).append(' ');
        }
        return names.toString();
    }

    static String registryValueKey(String registryTypeField, Object value) {
        if (value == null) {
            return "";
        }
        try {
            Class<?> registryTypes = type("org.spongepowered.api.registry.RegistryTypes");
            Object registryType = registryTypes.getField(registryTypeField).get(null);

            Object game = game();
            Object registry = method(game.getClass(), "registry",
                            type("org.spongepowered.api.registry.RegistryType"))
                    .invoke(game, registryType);

            Object key = method(registry.getClass(), "valueKey", Object.class)
                    .invoke(registry, value);
            return key == null ? "" : String.valueOf(key);
        } catch (Throwable unknown) {
            return "";
        }
    }

    static Object call(Object target, String name) throws ReflectiveOperationException {
        return method(target.getClass(), name).invoke(target);
    }

    static Object server() throws ReflectiveOperationException {
        return method(type("org.spongepowered.api.Sponge"), "server").invoke(null);
    }

    static Object game() throws ReflectiveOperationException {
        return method(type("org.spongepowered.api.Sponge"), "game").invoke(null);
    }

    static Object component(String legacy) throws ReflectiveOperationException {
        Class<?> serializerClass = type(
                "net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
        Object serializer = method(serializerClass, "legacyAmpersand").invoke(null);
        return method(serializer.getClass(), "deserialize", String.class)
                .invoke(serializer, legacy == null ? "" : legacy);
    }

    static void sendMessage(Object audience, String legacy) {
        try {
            Object text = component(legacy);
            Class<?> audienceClass = type("net.kyori.adventure.audience.Audience");
            Class<?> componentClass = type("net.kyori.adventure.text.Component");
            method(audienceClass, "sendMessage", componentClass).invoke(audience, text);
        } catch (Throwable ignored) {

        }
    }

    static Optional<Object> onlinePlayer(UUID uuid) {
        try {
            Object server = server();
            Object optional = method(server.getClass(), "player", UUID.class)
                    .invoke(server, uuid);
            return unwrap(optional);
        } catch (Throwable failure) {
            return Optional.empty();
        }
    }

    static Optional<Object> onlinePlayer(String name) {
        try {
            Object server = server();
            Object optional = method(server.getClass(), "player", String.class)
                    .invoke(server, name);
            return unwrap(optional);
        } catch (Throwable failure) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    static Optional<Object> unwrap(Object maybeOptional) {
        if (maybeOptional instanceof Optional) {
            return (Optional<Object>) maybeOptional;
        }
        return Optional.ofNullable(maybeOptional);
    }

    static Optional<Object> registryValue(String registryTypeField, String id) {
        try {
            Class<?> registryTypes = type("org.spongepowered.api.registry.RegistryTypes");
            Object registryType = registryTypes.getField(registryTypeField).get(null);

            Class<?> resourceKeyClass = type("org.spongepowered.api.ResourceKey");
            Object key = method(resourceKeyClass, "resolve", String.class)
                    .invoke(null, normaliseKey(id));

            Object game = game();
            Object registry = method(game.getClass(), "registry",
                    type("org.spongepowered.api.registry.RegistryType"))
                    .invoke(game, registryType);

            Object value = method(registry.getClass(), "findValue", resourceKeyClass)
                    .invoke(registry, key);
            return unwrap(value);
        } catch (Throwable unknown) {
            return Optional.empty();
        }
    }

    static String normaliseKey(String id) {
        if (id == null || id.isEmpty()) {
            return "minecraft:stone";
        }
        String lower = id.toLowerCase(java.util.Locale.ROOT).trim();
        return lower.indexOf(':') >= 0 ? lower : "minecraft:" + lower;
    }
}
