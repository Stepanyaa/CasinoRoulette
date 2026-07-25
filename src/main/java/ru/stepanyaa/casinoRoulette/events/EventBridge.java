package ru.stepanyaa.casinoRoulette.events;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public abstract class EventBridge {

    private final Map<Class<?>, List<Consumer<?>>> handlers = new ConcurrentHashMap<>();

    public <T> void subscribe(Class<T> type, Consumer<T> handler) {
        handlers.computeIfAbsent(type, key -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(handler);
    }

    @SuppressWarnings("unchecked")
    public <T> T post(T event) {
        List<Consumer<?>> list = handlers.get(event.getClass());
        if (list == null || list.isEmpty()) {
            return event;
        }
        for (Consumer<?> consumer : new ArrayList<>(list)) {
            try {
                ((Consumer<T>) consumer).accept(event);
            } catch (Throwable throwable) {
                logHandlerFailure(event, throwable);
            }
        }
        return event;
    }

    public boolean hasSubscribers(Class<?> type) {
        List<Consumer<?>> list = handlers.get(type);
        return list != null && !list.isEmpty();
    }

    public abstract void register();

    public void unregister() {
        handlers.clear();
    }

    protected abstract void logHandlerFailure(Object event, Throwable throwable);
}
