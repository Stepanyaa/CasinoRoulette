package ru.stepanyaa.casinoRoulette.platform.impl.paper;

import org.bukkit.plugin.java.JavaPlugin;
import ru.stepanyaa.casinoRoulette.platform.impl.bukkit.BukkitAdapter;
import ru.stepanyaa.casinoRoulette.platform.PlatformType;

public class SpigotAdapter extends BukkitAdapter {

    public SpigotAdapter(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public PlatformType type() {
        return PlatformType.SPIGOT;
    }
}
