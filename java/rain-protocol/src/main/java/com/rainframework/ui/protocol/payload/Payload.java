package com.rainframework.ui.protocol.payload;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainframework.ui.protocol.TypeSchema;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Typed access to a validated payload. Accessors never convert: each one only reads fields declared with its type.
 * {@code get*} requires a value; {@code find*} returns null when an optional field has none.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Payload {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TypeSchema.ObjectType schema;
    private final JsonNode value;

    /** {@code json} must already have passed the PayloadValidator for this schema. */
    public static Payload of(TypeSchema.ObjectType schema, String json) {
        try {
            return new Payload(schema, MAPPER.readTree(json));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Payload is not valid JSON; validate it before reading it", e);
        }
    }

    /** Also reads {@code t.asset()} fields, whose value is the asset's name. */
    public String getString(String key) {
        return required(key, findString(key));
    }

    public int getInt(String key) {
        return required(key, findInt(key));
    }

    public long getLong(String key) {
        return required(key, findLong(key));
    }

    public double getDouble(String key) {
        return required(key, findDouble(key));
    }

    public boolean getBool(String key) {
        return required(key, findBool(key));
    }

    public Payload getObject(String key) {
        return required(key, findObject(key));
    }

    public List<Payload> getObjectList(String key) {
        final var of = listOf(key);
        if (!(of instanceof TypeSchema.ObjectType object)) {
            throw mismatch(key, "a list of objects");
        }

        final var items = new ArrayList<Payload>();
        for (final var item : required(key, find(key))) {
            items.add(new Payload(object, item));
        }

        return List.copyOf(items);
    }

    public List<String> getStringList(String key) {
        final var of = listOf(key);
        if (!(of instanceof TypeSchema.StringType) && !(of instanceof TypeSchema.AssetType)) {
            throw mismatch(key, "a list of strings");
        }

        final var items = new ArrayList<String>();
        for (final var item : required(key, find(key))) {
            items.add(item.asText());
        }

        return List.copyOf(items);
    }

    public @Nullable String findString(String key) {
        final var declared = declared(key);
        if (!(declared instanceof TypeSchema.StringType) && !(declared instanceof TypeSchema.AssetType)) {
            throw mismatch(key, "a string");
        }

        final var node = find(key);
        return node == null ? null : node.asText();
    }

    public @Nullable Integer findInt(String key) {
        expect(key, TypeSchema.IntType.class, "an int");

        final var node = find(key);
        return node == null ? null : node.asInt();
    }

    public @Nullable Long findLong(String key) {
        expect(key, TypeSchema.LongType.class, "a long");

        final var node = find(key);
        return node == null ? null : node.asLong();
    }

    public @Nullable Double findDouble(String key) {
        expect(key, TypeSchema.DoubleType.class, "a double");

        final var node = find(key);
        return node == null ? null : node.asDouble();
    }

    public @Nullable Boolean findBool(String key) {
        expect(key, TypeSchema.BoolType.class, "a bool");

        final var node = find(key);
        return node == null ? null : node.asBoolean();
    }

    public @Nullable Payload findObject(String key) {
        if (!(declared(key) instanceof TypeSchema.ObjectType object)) {
            throw mismatch(key, "an object");
        }

        final var node = find(key);
        return node == null ? null : new Payload(object, node);
    }

    private TypeSchema declared(String key) {
        final var declared = schema.fields().get(key);
        if (declared == null) {
            throw new PayloadTypeException("Payload field \"" + key + "\" is not declared by the action");
        }

        return TypeSchema.unwrap(declared);
    }

    private TypeSchema listOf(String key) {
        if (!(declared(key) instanceof TypeSchema.ListType list)) {
            throw mismatch(key, "a list");
        }

        return TypeSchema.unwrap(list.of());
    }

    private void expect(String key, Class<? extends TypeSchema> type, String description) {
        if (!type.isInstance(declared(key))) {
            throw mismatch(key, description);
        }
    }

    // An explicit null and a missing key both mean "no value", as in the contract.
    private @Nullable JsonNode find(String key) {
        final var node = value.get(key);
        return node == null || node.isNull() ? null : node;
    }

    private static <T> T required(String key, @Nullable T value) {
        if (value == null) {
            throw new PayloadTypeException("Payload field \"" + key + "\" has no value; use find* for optional fields");
        }

        return value;
    }

    private PayloadTypeException mismatch(String key, String expected) {
        final var actual = schema.fields().get(key);
        return new PayloadTypeException("Payload field \"" + key + "\" is not " + expected + ", it is declared as "
                + TypeSchema.unwrap(actual).getClass().getSimpleName());
    }
}
