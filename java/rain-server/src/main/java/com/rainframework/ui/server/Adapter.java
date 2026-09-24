package com.rainframework.ui.server;

/** Writes the fields of one type into properties; only what the adapter writes reaches the client (spec §3.6). */
@FunctionalInterface
public interface Adapter<T> {

    void write(T value, PropertyWriter out);
}
