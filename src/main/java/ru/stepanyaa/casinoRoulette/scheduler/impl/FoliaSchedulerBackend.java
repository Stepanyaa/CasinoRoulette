package ru.stepanyaa.casinoRoulette.scheduler.impl;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoTask;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public final class FoliaSchedulerBackend extends BukkitSchedulerBackend {

    private final Object globalRegionScheduler;
    private final Object regionScheduler;
    private final Object asyncScheduler;

    private final Method globalRun;
    private final Method globalRunDelayed;
    private final Method globalRunAtFixedRate;

    private final Method regionRun;

    private final Method asyncRunNow;
    private final Method asyncRunDelayed;
    private final Method asyncRunAtFixedRate;

    private final Method getEntityScheduler;
    private final Method entityRun;
    private final Method entityRunDelayed;
    private final Method entityRunAtFixedRate;

    private final Method taskCancel;
    private final Method isOwnedByCurrentRegion;

    private final Set<CasinoTask> tracked =
            Collections.newSetFromMap(new WeakHashMap<CasinoTask, Boolean>());

    public FoliaSchedulerBackend(Plugin plugin) throws ReflectiveOperationException {
        super(plugin);

        Class<?> serverClass = Bukkit.getServer().getClass();
        Class<?> bukkitClass = Bukkit.class;

        this.globalRegionScheduler = bukkitClass.getMethod("getGlobalRegionScheduler").invoke(null);
        this.regionScheduler = bukkitClass.getMethod("getRegionScheduler").invoke(null);
        this.asyncScheduler = bukkitClass.getMethod("getAsyncScheduler").invoke(null);

        Class<?> globalClass = globalRegionScheduler.getClass();
        this.globalRun = find(globalClass, "run", Plugin.class, Consumer.class);
        this.globalRunDelayed = find(globalClass, "runDelayed", Plugin.class, Consumer.class, long.class);
        this.globalRunAtFixedRate =
                find(globalClass, "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);

        Class<?> regionClass = regionScheduler.getClass();
        this.regionRun = find(regionClass, "run",
                Plugin.class, org.bukkit.Location.class, Consumer.class);

        Class<?> asyncClass = asyncScheduler.getClass();
        this.asyncRunNow = find(asyncClass, "runNow", Plugin.class, Consumer.class);
        this.asyncRunDelayed = find(asyncClass, "runDelayed",
                Plugin.class, Consumer.class, long.class, java.util.concurrent.TimeUnit.class);
        this.asyncRunAtFixedRate = find(asyncClass, "runAtFixedRate",
                Plugin.class, Consumer.class, long.class, long.class, java.util.concurrent.TimeUnit.class);

        this.getEntityScheduler = org.bukkit.entity.Entity.class.getMethod("getScheduler");
        Class<?> entitySchedulerClass = getEntityScheduler.getReturnType();
        this.entityRun = find(entitySchedulerClass, "run", Plugin.class, Consumer.class, Runnable.class);
        this.entityRunDelayed = find(entitySchedulerClass, "runDelayed",
                Plugin.class, Consumer.class, Runnable.class, long.class);
        this.entityRunAtFixedRate = find(entitySchedulerClass, "runAtFixedRate",
                Plugin.class, Consumer.class, Runnable.class, long.class, long.class);

        Class<?> scheduledTaskClass =
                Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");
        this.taskCancel = scheduledTaskClass.getMethod("cancel");

        Method ownershipCheck = null;
        try {
            ownershipCheck = bukkitClass.getMethod("isOwnedByCurrentRegion", org.bukkit.entity.Entity.class);
        } catch (NoSuchMethodException ignored) {

        }
        this.isOwnedByCurrentRegion = ownershipCheck;

        if (serverClass == null) {
            throw new IllegalStateException("No server implementation");
        }
    }

    private static Method find(Class<?> owner, String name, Class<?>... params)
            throws NoSuchMethodException {
        Method method = owner.getMethod(name, params);
        method.setAccessible(true);
        return method;
    }

    private static long ticks(long value) {
        return Math.max(1L, value);
    }

    private CasinoTask track(final Object scheduledTask) {
        if (scheduledTask == null) {
            return CasinoTask.NOOP;
        }
        CasinoTask handle = new CasinoTask() {
            private volatile boolean cancelled;

            @Override
            public void cancel() {
                cancelled = true;
                try {
                    taskCancel.invoke(scheduledTask);
                } catch (Throwable ignored) {
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };
        synchronized (tracked) {
            tracked.add(handle);
        }
        return handle;
    }

    private static Consumer<Object> adapt(final Runnable body) {
        return new Consumer<Object>() {
            @Override
            public void accept(Object scheduledTask) {
                body.run();
            }
        };
    }

    private static Consumer<Object> adaptRepeating(final Consumer<CasinoTask> body, final Method cancelMethod) {
        return new Consumer<Object>() {
            @Override
            public void accept(final Object scheduledTask) {
                body.accept(new CasinoTask() {
                    private volatile boolean cancelled;

                    @Override
                    public void cancel() {
                        cancelled = true;
                        try {
                            cancelMethod.invoke(scheduledTask);
                        } catch (Throwable ignored) {
                        }
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelled;
                    }
                });
            }
        };
    }

    private CasinoTask invoke(Method method, Object target, Object... args) {
        if (!canSchedule()) {
            return CasinoTask.NOOP;
        }
        try {
            return track(method.invoke(target, args));
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Folia scheduling failed: " + throwable);
            return CasinoTask.NOOP;
        }
    }

    @Override
    public CasinoTask run(Runnable task) {
        if (!canSchedule()) {
            task.run();
            return CasinoTask.NOOP;
        }
        return invoke(globalRun, globalRegionScheduler, plugin, adapt(task));
    }

    @Override
    public CasinoTask delay(long delayTicks, Runnable task) {
        return invoke(globalRunDelayed, globalRegionScheduler, plugin, adapt(task), ticks(delayTicks));
    }

    @Override
    public CasinoTask timer(long delayTicks, long periodTicks, Consumer<CasinoTask> task) {
        return invoke(globalRunAtFixedRate, globalRegionScheduler, plugin,
                adaptRepeating(task, taskCancel), ticks(delayTicks), ticks(periodTicks));
    }

    @Override
    public CasinoTask async(Runnable task) {
        if (!canSchedule()) {
            task.run();
            return CasinoTask.NOOP;
        }
        return invoke(asyncRunNow, asyncScheduler, plugin, adapt(task));
    }

    @Override
    public CasinoTask asyncTimer(long delayTicks, long periodTicks, Consumer<CasinoTask> task) {

        long delayMs = ticks(delayTicks) * 50L;
        long periodMs = ticks(periodTicks) * 50L;
        return invoke(asyncRunAtFixedRate, asyncScheduler, plugin,
                adaptRepeating(task, taskCancel), delayMs, periodMs,
                java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    @Override
    public CasinoTask runAtEntity(Object entityHandle, Runnable task, Runnable retired) {
        Object entityScheduler = entityScheduler(entityHandle);
        if (entityScheduler == null) {
            return run(task);
        }
        return invoke(entityRun, entityScheduler, plugin, adapt(task), retiredOrNoop(retired));
    }

    @Override
    public CasinoTask delayAtEntity(Object entityHandle, long delayTicks, Runnable task, Runnable retired) {
        Object entityScheduler = entityScheduler(entityHandle);
        if (entityScheduler == null) {
            return delay(delayTicks, task);
        }
        return invoke(entityRunDelayed, entityScheduler, plugin,
                adapt(task), retiredOrNoop(retired), ticks(delayTicks));
    }

    @Override
    public CasinoTask timerAtEntity(Object entityHandle, long delayTicks, long periodTicks,
                                    Consumer<CasinoTask> task, Runnable retired) {
        Object entityScheduler = entityScheduler(entityHandle);
        if (entityScheduler == null) {
            return timer(delayTicks, periodTicks, task);
        }
        return invoke(entityRunAtFixedRate, entityScheduler, plugin,
                adaptRepeating(task, taskCancel), retiredOrNoop(retired),
                ticks(delayTicks), ticks(periodTicks));
    }

    private Object entityScheduler(Object entityHandle) {
        if (!(entityHandle instanceof org.bukkit.entity.Entity)) {
            return null;
        }
        try {
            return getEntityScheduler.invoke(entityHandle);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static Runnable retiredOrNoop(Runnable retired) {
        return retired != null ? retired : new Runnable() {
            @Override
            public void run() {

            }
        };
    }

    @Override
    public CasinoTask runAtLocation(Object locationHandle, Runnable task) {
        if (!(locationHandle instanceof org.bukkit.Location)) {
            return run(task);
        }
        return invoke(regionRun, regionScheduler, plugin, locationHandle, adapt(task));
    }

    @Override
    public boolean isOnOwningThread(Object handle) {
        if (isOwnedByCurrentRegion != null && handle instanceof org.bukkit.entity.Entity) {
            try {
                return Boolean.TRUE.equals(isOwnedByCurrentRegion.invoke(null, handle));
            } catch (Throwable ignored) {
            }
        }

        return true;
    }

    @Override
    public void cancelAll() {
        synchronized (tracked) {
            for (CasinoTask task : tracked) {
                try {
                    task.cancel();
                } catch (Throwable ignored) {
                }
            }
            tracked.clear();
        }

    }
}
