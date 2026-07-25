package ru.stepanyaa.casinoRoulette.scheduler;

import java.util.function.Consumer;

public interface SchedulerBackend {

    CasinoTask run(Runnable task);

    CasinoTask runAtEntity(Object entityHandle, Runnable task, Runnable retired);

    CasinoTask runAtLocation(Object locationHandle, Runnable task);

    CasinoTask async(Runnable task);

    CasinoTask delay(long delayTicks, Runnable task);

    CasinoTask delayAtEntity(Object entityHandle, long delayTicks, Runnable task, Runnable retired);

    CasinoTask timer(long delayTicks, long periodTicks, Consumer<CasinoTask> task);

    CasinoTask timerAtEntity(Object entityHandle, long delayTicks, long periodTicks,
                             Consumer<CasinoTask> task, Runnable retired);

    CasinoTask asyncTimer(long delayTicks, long periodTicks, Consumer<CasinoTask> task);

    boolean isOnOwningThread(Object handle);

    void cancelAll();
}
