package ru.stepanyaa.casinoRoulette.scheduler.impl;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoTask;
import ru.stepanyaa.casinoRoulette.scheduler.SchedulerBackend;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

public class BukkitSchedulerBackend implements SchedulerBackend {

    protected final Plugin plugin;
    private final Set<CasinoTask> active =
            Collections.newSetFromMap(new WeakHashMap<CasinoTask, Boolean>());

    public BukkitSchedulerBackend(Plugin plugin) {
        this.plugin = plugin;
    }

    private CasinoTask track(CasinoTask task) {
        synchronized (active) {
            active.add(task);
        }
        return task;
    }

    protected boolean canSchedule() {
        return plugin.isEnabled();
    }

    private static CasinoTask wrap(final BukkitTask task) {
        return new CasinoTask() {
            private volatile boolean cancelled;

            @Override
            public void cancel() {
                cancelled = true;
                try {
                    task.cancel();
                } catch (Throwable ignored) {

                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };
    }

    @Override
    public CasinoTask run(Runnable task) {
        if (!canSchedule()) {

            task.run();
            return CasinoTask.NOOP;
        }
        return track(wrap(Bukkit.getScheduler().runTask(plugin, task)));
    }

    @Override
    public CasinoTask async(Runnable task) {
        if (!canSchedule()) {
            task.run();
            return CasinoTask.NOOP;
        }
        return track(wrap(Bukkit.getScheduler().runTaskAsynchronously(plugin, task)));
    }

    @Override
    public CasinoTask delay(long delayTicks, Runnable task) {
        if (!canSchedule()) {
            return CasinoTask.NOOP;
        }
        return track(wrap(Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, delayTicks))));
    }

    @Override
    public CasinoTask timer(long delayTicks, long periodTicks, final Consumer<CasinoTask> task) {
        if (!canSchedule()) {
            return CasinoTask.NOOP;
        }
        final SelfCancellingRunnable runnable = new SelfCancellingRunnable(task);
        BukkitTask bukkitTask = runnable.runTaskTimer(plugin, Math.max(0L, delayTicks), Math.max(1L, periodTicks));
        runnable.attach(wrap(bukkitTask));
        return track(runnable.handle());
    }

    @Override
    public CasinoTask asyncTimer(long delayTicks, long periodTicks, final Consumer<CasinoTask> task) {
        if (!canSchedule()) {
            return CasinoTask.NOOP;
        }
        final SelfCancellingRunnable runnable = new SelfCancellingRunnable(task);
        BukkitTask bukkitTask = runnable.runTaskTimerAsynchronously(
                plugin, Math.max(0L, delayTicks), Math.max(1L, periodTicks));
        runnable.attach(wrap(bukkitTask));
        return track(runnable.handle());
    }

    @Override
    public CasinoTask runAtEntity(Object entityHandle, Runnable task, Runnable retired) {
        return run(task);
    }

    @Override
    public CasinoTask runAtLocation(Object locationHandle, Runnable task) {
        return run(task);
    }

    @Override
    public CasinoTask delayAtEntity(Object entityHandle, long delayTicks, Runnable task, Runnable retired) {
        return delay(delayTicks, task);
    }

    @Override
    public CasinoTask timerAtEntity(Object entityHandle, long delayTicks, long periodTicks,
                                    Consumer<CasinoTask> task, Runnable retired) {
        return timer(delayTicks, periodTicks, task);
    }

    @Override
    public boolean isOnOwningThread(Object handle) {
        return Bukkit.isPrimaryThread();
    }

    @Override
    public void cancelAll() {
        synchronized (active) {
            for (CasinoTask task : active) {
                try {
                    task.cancel();
                } catch (Throwable ignored) {
                }
            }
            active.clear();
        }
        try {
            Bukkit.getScheduler().cancelTasks(plugin);
        } catch (Throwable ignored) {
        }
    }

    private static final class SelfCancellingRunnable extends BukkitRunnable {
        private final Consumer<CasinoTask> body;
        private volatile CasinoTask handle;
        private volatile boolean cancelled;

        private final CasinoTask facade = new CasinoTask() {
            @Override
            public void cancel() {
                cancelled = true;
                CasinoTask delegate = handle;
                if (delegate != null) {
                    delegate.cancel();
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };

        SelfCancellingRunnable(Consumer<CasinoTask> body) {
            this.body = body;
        }

        void attach(CasinoTask delegate) {
            this.handle = delegate;
            if (cancelled) {
                delegate.cancel();
            }
        }

        CasinoTask handle() {
            return facade;
        }

        @Override
        public void run() {
            if (cancelled) {
                return;
            }
            body.accept(facade);
        }
    }
}
