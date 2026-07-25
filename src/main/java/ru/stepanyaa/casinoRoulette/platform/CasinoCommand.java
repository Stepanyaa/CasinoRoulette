package ru.stepanyaa.casinoRoulette.platform;

import java.util.Collections;
import java.util.List;

public interface CasinoCommand {

    String name();

    default List<String> aliases() {
        return Collections.emptyList();
    }

    String description();

    default String permission() {
        return null;
    }

    boolean execute(CasinoSender sender, String[] args);

    default List<String> complete(CasinoSender sender, String[] args) {
        return Collections.emptyList();
    }
}
