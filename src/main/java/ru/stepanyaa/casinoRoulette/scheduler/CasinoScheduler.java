package ru.stepanyaa.casinoRoulette.scheduler;

import java.util.function.Consumer;

public final class CasinoScheduler {

    private static volatile SchedulerBackend backend;

    private CasinoScheduler() {
    }

    public static void install(SchedulerBackend schedulerBackend) {
        backend = schedulerBackend;
    }

    public static SchedulerBackend backend() {
        SchedulerBackend current = backend;
        if (current == null) {
            throw new IllegalStateException(
                    "CasinoScheduler used before the platform adapter was installed.");
        }
        return current;
    }

    public static boolean isReady() {
        return backend != null;
    }

    public static CasinoTask run(Runnable task) {
        return backend().run(task);
    }

    public static CasinoTask async(Runnable task) {
        return backend().async(task);
    }

    public static CasinoTask delay(long delayTicks, Runnable task) {
        return backend().delay(delayTicks, task);
    }

    public static CasinoTask timer(long delayTicks, long periodTicks, Consumer<CasinoTask> task) {
        return backend().timer(delayTicks, periodTicks, task);
    }

    public static CasinoTask asyncTimer(long delayTicks, long periodTicks, Consumer<CasinoTask> task) {
        return backend().asyncTimer(delayTicks, periodTicks, task);
    }

    public static CasinoTask atEntity(Object entityHandle, Runnable task) {
        return backend().runAtEntity(entityHandle, task, null);
    }

    public static CasinoTask atEntity(Object entityHandle, Runnable task, Runnable retired) {
        return backend().runAtEntity(entityHandle, task, retired);
    }

    public static CasinoTask delayAtEntity(Object entityHandle, long delayTicks, Runnable task) {
        return backend().delayAtEntity(entityHandle, delayTicks, task, null);
    }

    public static CasinoTask timerAtEntity(Object entityHandle, long delayTicks, long periodTicks,
                                           Consumer<CasinoTask> task) {
        return backend().timerAtEntity(entityHandle, delayTicks, periodTicks, task, null);
    }

    public static CasinoTask atLocation(Object locationHandle, Runnable task) {
        return backend().runAtLocation(locationHandle, task);
    }

    public static void shutdown() {
        SchedulerBackend current = backend;
        if (current != null) {
            current.cancelAll();
        }
        backend = null;
    }
}
