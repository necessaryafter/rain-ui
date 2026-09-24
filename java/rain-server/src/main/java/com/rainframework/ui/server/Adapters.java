package com.rainframework.ui.server;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Adapters by type. A value uses the adapter of its class, else the nearest superclass or interface that has one. */
public final class Adapters {
    private final Map<Class<?>, Adapter<?>> adapters = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, Adapter<? super T> adapter) {
        adapters.put(type, adapter);
    }

    @SuppressWarnings("unchecked")
    @Nullable Adapter<Object> find(Class<?> type) {
        final var pending = new ArrayDeque<Class<?>>();
        pending.add(type);

        while (!pending.isEmpty()) {
            final var current = pending.poll();
            final var adapter = adapters.get(current);
            if (adapter != null) {
                return (Adapter<Object>) adapter;
            }

            if (current.getSuperclass() != null) {
                pending.add(current.getSuperclass());
            }

            pending.addAll(List.of(current.getInterfaces()));
        }

        return null;
    }
}
