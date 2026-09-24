package com.rainframework.ui.server;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Subscribers are called in subscription order. */
public final class Event<T> {
    private final List<Consumer<T>> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<T> listener) {
        listeners.add(listener);
    }

    void fire(T event) {
        for (final var listener : listeners) {
            listener.accept(event);
        }
    }
}
