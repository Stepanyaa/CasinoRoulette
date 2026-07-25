package ru.stepanyaa.casinoRoulette.platform.impl.sponge;

import ru.stepanyaa.casinoRoulette.platform.CasinoCommand;
import ru.stepanyaa.casinoRoulette.platform.CasinoSender;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

final class SpongeCommandRegistrar {

    private SpongeCommandRegistrar() {
    }

    static void register(Object registerCommandEvent, Object pluginContainer,
                         final CasinoCommand command, final SpongeAdapter adapter,
                         final Logger logger) throws ReflectiveOperationException {

        final Class<?> rawClass = SpongeReflection.type(
                "org.spongepowered.api.command.Command$Raw");

        Object raw = Proxy.newProxyInstance(
                rawClass.getClassLoader(),
                new Class<?>[]{rawClass},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args)
                            throws Throwable {
                        switch (method.getName()) {
                            case "process":
                                return process(args, command, adapter, logger);
                            case "complete":
                                return complete(args, command, adapter);
                            case "canExecute":
                                return canExecute(args, command, adapter);
                            case "shortDescription":
                            case "help":
                                return java.util.Optional.of(
                                        SpongeReflection.component(command.description()));
                            case "usage":
                                return SpongeReflection.component("/" + command.name());
                            case "toString":
                                return "CasinoRoulette command /" + command.name();
                            case "hashCode":
                                return System.identityHashCode(proxy);
                            case "equals":
                                return proxy == args[0];
                            default:
                                if (method.isDefault()) {
                                    return invokeDefault(proxy, method, args);
                                }
                                return null;
                        }
                    }
                });

        List<String> aliases = new ArrayList<>();
        aliases.add(command.name());
        if (command.aliases() != null) {
            aliases.addAll(command.aliases());
        }

        String primary = aliases.get(0);
        String[] secondary = aliases.subList(1, aliases.size()).toArray(new String[0]);

        Method register = null;
        for (Method candidate : registerCommandEvent.getClass().getMethods()) {
            Class<?>[] parameters = candidate.getParameterTypes();
            if ("register".equals(candidate.getName()) && parameters.length == 4
                    && parameters[0].isInstance(pluginContainer)
                    && parameters[1].isAssignableFrom(rawClass)
                    && parameters[2] == String.class && parameters[3] == String[].class) {
                register = candidate;
                break;
            }
        }
        if (register == null) {
            throw new NoSuchMethodException("No compatible RegisterCommandEvent.register overload");
        }
        register.invoke(registerCommandEvent, pluginContainer, raw, primary, secondary);
    }

    private static Object invokeDefault(Object proxy, Method method, Object[] args) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup());
        return lookup.unreflectSpecial(method, method.getDeclaringClass()).bindTo(proxy)
                .invokeWithArguments(args == null ? new Object[0] : args);
    }

    private static Object process(Object[] args, CasinoCommand command,
                                  SpongeAdapter adapter, Logger logger) throws Exception {
        CasinoSender sender = adapter.senderFor(args[0]);

        String permission = command.permission();
        if (permission != null && !sender.hasPermission(permission)) {
            sender.sendMessage("&cYou do not have permission to use that command.");
            return success();
        }

        try {
            if (!command.execute(sender, split(args[1]))) {
                sender.sendMessage("&cUsage: /" + command.name());
            }
        } catch (Throwable failure) {
            sender.sendMessage("&cAn internal error occurred while running that command.");
            logger.warning("/" + command.name() + " failed: " + failure);
        }
        return success();
    }

    private static Object success() throws ReflectiveOperationException {
        Class<?> resultClass = SpongeReflection.type("org.spongepowered.api.command.CommandResult");
        return SpongeReflection.method(resultClass, "success").invoke(null);
    }

    private static Object complete(Object[] args, CasinoCommand command,
                                   SpongeAdapter adapter) throws Exception {
        List<Object> completions = new ArrayList<>();
        try {
            CasinoSender sender = adapter.senderFor(args[0]);
            List<String> suggestions = command.complete(sender, split(args[1]));
            if (suggestions != null) {
                Class<?> completionClass = SpongeReflection
                        .type("org.spongepowered.api.command.CommandCompletion");
                Method of = SpongeReflection.method(completionClass, "of", String.class);
                for (String suggestion : suggestions) {
                    completions.add(of.invoke(null, suggestion));
                }
            }
        } catch (Throwable ignored) {

        }
        return completions;
    }

    private static Object canExecute(Object[] args, CasinoCommand command,
                                     SpongeAdapter adapter) {
        String permission = command.permission();
        if (permission == null) {
            return Boolean.TRUE;
        }
        return adapter.senderFor(args[0]).hasPermission(permission);
    }

    private static String[] split(Object argumentReader) {
        String remaining;
        try {
            Object value = SpongeReflection.call(argumentReader, "remaining");
            remaining = value == null ? "" : value.toString();
        } catch (Throwable failure) {
            remaining = "";
        }
        remaining = remaining.trim();
        return remaining.isEmpty() ? new String[0] : remaining.split("\\s+");
    }
}
