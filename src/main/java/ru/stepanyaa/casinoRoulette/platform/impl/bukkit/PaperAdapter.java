package ru.stepanyaa.casinoRoulette.platform.impl.bukkit;

import org.bukkit.plugin.java.JavaPlugin;
import ru.stepanyaa.casinoRoulette.platform.PlatformType;

public class PaperAdapter extends SpigotAdapter {

    public PaperAdapter(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public PlatformType type() {
        return PlatformType.PAPER;
    }
}
