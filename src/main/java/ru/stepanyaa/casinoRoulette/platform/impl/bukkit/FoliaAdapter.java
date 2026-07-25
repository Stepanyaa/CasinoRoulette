package ru.stepanyaa.casinoRoulette.platform.impl.bukkit;

import org.bukkit.plugin.java.JavaPlugin;
import ru.stepanyaa.casinoRoulette.platform.PlatformType;
import ru.stepanyaa.casinoRoulette.scheduler.SchedulerBackend;
import ru.stepanyaa.casinoRoulette.scheduler.impl.FoliaSchedulerBackend;

public class FoliaAdapter extends PaperAdapter {

    public FoliaAdapter(JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public PlatformType type() {
        return PlatformType.FOLIA;
    }

    @Override
    protected SchedulerBackend createSchedulerBackend() {
        try {
            return new FoliaSchedulerBackend(plugin);
        } catch (ReflectiveOperationException unavailable) {
            throw new IllegalStateException(
                    "Detected Folia, but its region schedulers could not be resolved ("
                            + unavailable + "). This build of Folia is not supported.",
                    unavailable);
        }
    }
}
