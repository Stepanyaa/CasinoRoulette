package ru.stepanyaa.casinoRoulette.platform.impl.bukkit;

import org.bukkit.plugin.java.JavaPlugin;
import ru.stepanyaa.casinoRoulette.platform.PlatformType;

public class PurpurAdapter extends PaperAdapter {

    public PurpurAdapter(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public PlatformType type() {
        return PlatformType.PURPUR;
    }
}
