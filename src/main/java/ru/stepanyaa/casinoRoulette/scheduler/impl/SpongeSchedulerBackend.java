package ru.stepanyaa.casinoRoulette.scheduler.impl;

import ru.stepanyaa.casinoRoulette.scheduler.CasinoTask;
import ru.stepanyaa.casinoRoulette.scheduler.SchedulerBackend;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

public final class SpongeSchedulerBackend implements SchedulerBackend {

    private static final long MILLIS_PER_TICK = 50L;

    private final Object pluginContainer;
    private final Logger logger;

    private final Class<?> taskClass;
    private final Class<?> taskBuilderClass;
    private final Class<?> ticksClass;
    private final Method taskBuilder;
    private final Method builderExecutor;
    private final Method builderDelayTicks;
    private final Method builderIntervalTicks;
    private final Method builderPlugin;
    private final Method builderBuild;
    private final Method ticksOf;

    private final Set<Object> tracked =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private volatile boolean shuttingDown;

    public SpongeSchedulerBackend(Object pluginContainer, Logger logger) {
        this.pluginContainer = pluginContainer;
        this.logger = logger;
        try {
            this.taskClass = Class.forName("org.spongepowered.api.scheduler.Task");
            this.taskBuilderClass = Class.forName("org.spongepowered.api.scheduler.Task$Builder");
            this.ticksClass = Class.forName("org.spongepowered.api.util.Ticks");

            this.taskBuilder = taskClass.getMethod("builder");
            this.builderExecutor = taskBuilderClass.getMethod("execute", Runnable.class);
            this.builderDelayTicks = taskBuilderClass.getMethod("delay", ticksClass);
            this.builderIntervalTicks = taskBuilderClass.getMethod("interval", ticksClass);
            this.builderPlugin = taskBuilderClass.getMethod("plugin",
                    Class.forName("org.spongepowered.plugin.PluginContainer"));
            this.builderBuild = taskBuilderClass.getMethod("build");
            this.ticksOf = ticksClass.getMethod("of", long.class);
        } catch (ReflectiveOperationException missing) {
            throw new IllegalStateException(
                    "Sponge scheduler API not found; this backend must only be used on Sponge.",
                    missing);
        }
    }

    private Object ticks(long value) throws ReflectiveOperationException {

        return ticksOf.invoke(null, Math.max(0L, value));
    }

    private Object buildTask(Runnable body, long delayTicks, long periodTicks)
            throws ReflectiveOperationException {
        Object builder = taskBuilder.invoke(null);
        builder = builderExecutor.invoke(builder, body);
        builder = builderDelayTicks.invoke(builder, ticks(delayTicks));
        if (periodTicks > 0) {

            builder = builderIntervalTicks.invoke(builder, ticks(Math.max(1L, periodTicks)));
        }
        builder = builderPlugin.invoke(builder, pluginContainer);
        return builderBuild.invoke(builder);
    }

    private Object serverScheduler() throws ReflectiveOperationException {
        Class<?> sponge = Class.forName("org.spongepowered.api.Sponge");
        Object server = sponge.getMethod("server").invoke(null);
        return server.getClass().getMethod("scheduler").invoke(server);
    }

    private Object asyncScheduler() throws ReflectiveOperationException {
        Class<?> sponge = Class.forName("org.spongepowered.api.Sponge");
        return sponge.getMethod("asyncScheduler").invoke(null);
    }

    private CasinoTask submit(Object scheduler, Object task) throws ReflectiveOperationException {
        Object handle = scheduler.getClass()
                .getMethod("submit", taskClass)
                .invoke(scheduler, task);
        tracked.add(handle);
        return wrap(handle);
    }

    private CasinoTask wrap(final Object scheduledTask) {
        return new CasinoTask() {
            private volatile boolean cancelled;

            @Override
            public void cancel() {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                tracked.remove(scheduledTask);
                try {
                    scheduledTask.getClass().getMethod("cancel").invoke(scheduledTask);
                } catch (ReflectiveOperationException failure) {
                    logger.warning("Could not cancel a Sponge task: " + failure);
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled;
            }
        };
    }

    private CasinoTask inline(Runnable body, Throwable reason) {
        if (reason != null && !shuttingDown) {
            logger.warning("Sponge scheduling failed, running inline: " + reason);
        }
        try {
            body.run();
        } catch (Throwable failure) {
            logger.warning("Inline task execution failed: " + failure);
        }
        return CasinoTask.NOOP;
    }

    private static final class RepeatingBody implements Runnable {
        private final Consumer<CasinoTask> body;
        private volatile CasinoTask handle;

        RepeatingBody(Consumer<CasinoTask> body) {
            this.body = body;
        }

        void bind(CasinoTask handle) {
            this.handle = handle;
        }

        @Override
        public void run() {
            CasinoTask current = handle;
            body.accept(current == null ? CasinoTask.NOOP : current);
        }
    }

    private CasinoTask repeating(boolean async, long delayTicks, long periodTicks,
                                 Consumer<CasinoTask> body) {
        RepeatingBody runnable = new RepeatingBody(body);
        try {
            Object scheduler = async ? asyncScheduler() : serverScheduler();
            Object task = async
                    ? buildAsync(runnable, delayTicks, periodTicks)
                    : buildTask(runnable, delayTicks, periodTicks);
            CasinoTask handle = submit(scheduler, task);
            runnable.bind(handle);
            return handle;
        } catch (Throwable failure) {
            return inline(runnable, failure);
        }
    }

    private Object buildAsync(Runnable body, long delayTicks, long periodTicks)
            throws ReflectiveOperationException {
        Object builder = taskBuilder.invoke(null);
        builder = builderExecutor.invoke(builder, body);

        Method delayMillis = taskBuilderClass.getMethod("delay", long.class,
                java.util.concurrent.TimeUnit.class);
        builder = delayMillis.invoke(builder, Math.max(0L, delayTicks) * MILLIS_PER_TICK,
                java.util.concurrent.TimeUnit.MILLISECONDS);

        if (periodTicks > 0) {
            Method intervalMillis = taskBuilderClass.getMethod("interval", long.class,
                    java.util.concurrent.TimeUnit.class);
            builder = intervalMillis.invoke(builder,
                    Math.max(1L, periodTicks) * MILLIS_PER_TICK,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        builder = builderPlugin.invoke(builder, pluginContainer);
        return builderBuild.invoke(builder);
    }

    @Override
    public CasinoTask run(Runnable task) {
        return delay(0L, task);
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
    public CasinoTask async(Runnable task) {
        try {
            return submit(asyncScheduler(), buildAsync(task, 0L, 0L));
        } catch (Throwable failure) {
            return inline(task, failure);
        }
    }

    @Override
    public CasinoTask delay(long delayTicks, Runnable task) {
        try {
            return submit(serverScheduler(), buildTask(task, delayTicks, 0L));
        } catch (Throwable failure) {
            return inline(task, failure);
        }
    }

    @Override
    public CasinoTask delayAtEntity(Object entityHandle, long delayTicks,
                                    Runnable task, Runnable retired) {
        return delay(delayTicks, task);
    }

    @Override
    public CasinoTask timer(long delayTicks, long periodTicks, Consumer<CasinoTask> task) {
        return repeating(false, delayTicks, periodTicks, task);
    }

    @Override
    public CasinoTask timerAtEntity(Object entityHandle, long delayTicks, long periodTicks,
                                    Consumer<CasinoTask> task, Runnable retired) {
        return timer(delayTicks, periodTicks, task);
    }

    @Override
    public CasinoTask asyncTimer(long delayTicks, long periodTicks, Consumer<CasinoTask> task) {
        return repeating(true, delayTicks, periodTicks, task);
    }

    @Override
    public boolean isOnOwningThread(Object handle) {
        try {
            Class<?> sponge = Class.forName("org.spongepowered.api.Sponge");
            Object server = sponge.getMethod("server").invoke(null);
            Object onMainThread = server.getClass()
                    .getMethod("onMainThread").invoke(server);
            return Boolean.TRUE.equals(onMainThread);
        } catch (Throwable unavailable) {

            return false;
        }
    }

    @Override
    public void cancelAll() {
        shuttingDown = true;
        Object[] snapshot;
        synchronized (tracked) {
            snapshot = tracked.toArray();
        }
        for (Object task : snapshot) {
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (Throwable ignored) {

            }
        }
        tracked.clear();
    }
}
