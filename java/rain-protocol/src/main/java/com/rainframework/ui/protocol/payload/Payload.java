package com.rainframework.ui.protocol.payload;

import com.rainframework.ui.protocol.TypeSchema;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Typed access to a validated payload. Accessors never convert: each one only reads fields declared with its type.
 * {@code get*} requires a value; {@code find*} returns null when an optional field has none.
 */
public final class Payload {

    /** {@code json} must already have passed the PayloadValidator for this schema. */
    public static Payload of(TypeSchema.ObjectType schema, String json) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /** Also reads {@code t.asset()} fields, whose value is the asset's name. */
    public String getString(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public int getInt(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public long getLong(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public double getDouble(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public boolean getBool(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public Payload getObject(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<Payload> getObjectList(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public List<String> getStringList(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public @Nullable String findString(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public @Nullable Integer findInt(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public @Nullable Long findLong(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public @Nullable Double findDouble(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public @Nullable Boolean findBool(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public @Nullable Payload findObject(String key) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
