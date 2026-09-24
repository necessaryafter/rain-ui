package com.rainframework.ui.server;

import com.fasterxml.jackson.databind.JsonNode;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The values a server sends to a screen, before conversion. They are converted with the registered adapters and
 * validated against the contract when the screen is opened or updated, so a wrong key or type fails on the server.
 */
public final class Properties {
    private final Map<String, @Nullable Object> values;

    private Properties(Map<String, @Nullable Object> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Properties taken as they are from a JSON object, e.g. a sample file; they are still validated on open. */
    public static Properties fromJson(JsonNode object) {
        if (!object.isObject()) {
            throw new IllegalArgumentException("Properties must be a JSON object");
        }

        final var values = new LinkedHashMap<String, @Nullable Object>();
        for (final var entry : object.properties()) {
            values.put(entry.getKey(), entry.getValue());
        }

        return new Properties(values);
    }

    public static Properties empty() {
        return new Properties(Map.of());
    }

    Map<String, @Nullable Object> values() {
        return values;
    }

    public static final class Builder {
        private final Map<String, @Nullable Object> values = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder add(String key, @Nullable Object value) {
            values.put(key, value);
            return this;
        }

        public Properties build() {
            return new Properties(new LinkedHashMap<>(values));
        }
    }
}
