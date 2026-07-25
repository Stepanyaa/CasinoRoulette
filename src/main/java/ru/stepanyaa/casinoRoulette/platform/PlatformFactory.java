package ru.stepanyaa.casinoRoulette.platform;

import ru.stepanyaa.casinoRoulette.platform.impl.bukkit.BukkitAdapter;
import ru.stepanyaa.casinoRoulette.platform.impl.folia.FoliaAdapter;
import ru.stepanyaa.casinoRoulette.platform.impl.paper.PaperAdapter;
import ru.stepanyaa.casinoRoulette.platform.impl.paper.PurpurAdapter;
import ru.stepanyaa.casinoRoulette.platform.impl.paper.SpigotAdapter;

import java.util.logging.Logger;

public final class PlatformFactory {

    private PlatformFactory() {
    }

    public static PlatformAdapter createBukkit(Object plugin, Logger logger) {
        org.bukkit.plugin.java.JavaPlugin javaPlugin =
                (org.bukkit.plugin.java.JavaPlugin) plugin;

        PlatformType detected = PlatformDetector.detect();
        PlatformDetector.logDetection(logger, detected, describeServer());

        switch (detected) {
            case FOLIA:
                return new FoliaAdapter(javaPlugin);
            case PURPUR:
                return new PurpurAdapter(javaPlugin);
            case PAPER:
                return new PaperAdapter(javaPlugin);
            case SPIGOT:
                return new SpigotAdapter(javaPlugin);
            case BUKKIT:
                return new BukkitAdapter(javaPlugin);
            default:

                logger.warning("Unrecognised server implementation, "
                        + "falling back to the generic Bukkit adapter.");
                return new BukkitAdapter(javaPlugin);
        }
    }

    private static String describeServer() {
        try {
            return org.bukkit.Bukkit.getName() + " " + org.bukkit.Bukkit.getVersion();
        } catch (Throwable notBukkit) {
            return "unknown";
        }
    }
}
