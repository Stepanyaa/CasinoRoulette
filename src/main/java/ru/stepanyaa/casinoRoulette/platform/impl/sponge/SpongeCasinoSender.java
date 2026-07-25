package ru.stepanyaa.casinoRoulette.platform.impl.sponge;

import ru.stepanyaa.casinoRoulette.platform.CasinoSender;

class SpongeCasinoSender implements CasinoSender {

    protected final Object subject;
    private final String name;
    private final boolean console;

    SpongeCasinoSender(Object subject, String name, boolean console) {
        this.subject = subject;
        this.name = name;
        this.console = console;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void sendMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        SpongeReflection.sendMessage(subject, message);
    }

    @Override
    public boolean hasPermission(String permission) {
        if (permission == null || permission.isEmpty()) {
            return true;
        }

        if (console) {
            return true;
        }
        try {
            Object result = SpongeReflection
                    .method(SpongeReflection.type("org.spongepowered.api.service.permission.Subject"),
                            "hasPermission", String.class)
                    .invoke(subject, permission);
            return Boolean.TRUE.equals(result);
        } catch (Throwable failure) {

            return false;
        }
    }

    @Override
    public boolean isPlayer() {
        return false;
    }

    @Override
    public boolean isConsole() {
        return console;
    }

    @Override
    public Object handle() {
        return subject;
    }
}
