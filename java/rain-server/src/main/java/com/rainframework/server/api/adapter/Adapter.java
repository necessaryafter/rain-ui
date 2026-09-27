package com.rainframework.server.api.adapter;

import com.rainframework.server.api.PropertyWriter;

/** Writes the fields of one type into properties; only what the adapter writes reaches the client (spec §3.6). */
@FunctionalInterface
public interface Adapter<T> {

    void write(T value, PropertyWriter out);
}
