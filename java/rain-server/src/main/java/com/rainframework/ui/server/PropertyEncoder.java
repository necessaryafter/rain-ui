package com.rainframework.ui.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

/**
 * Turns property values into JSON: strings, numbers, booleans, null, maps, lists and arrays as they are, and every
 * other type only through a registered adapter. Nothing is read by reflection (spec §3.6).
 */
@RequiredArgsConstructor
final class PropertyEncoder {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final Adapters adapters;
    private final ServerPlatform platform;

    ObjectNode encode(Properties properties) {
        final var object = JSON.objectNode();
        for (final var entry : properties.values().entrySet()) {
            object.set(entry.getKey(), encode(entry.getValue()));
        }

        return object;
    }

    private JsonNode encode(@Nullable Object value) {
        return switch (value) {
            case null -> JSON.nullNode();
            case JsonNode node -> node.deepCopy();
            case String text -> JSON.textNode(text);
            case Boolean bool -> JSON.booleanNode(bool);
            case Integer number -> JSON.numberNode(number);
            case Long number -> JSON.numberNode(number);
            case Short number -> JSON.numberNode(number);
            case Byte number -> JSON.numberNode(number);
            case Double number -> JSON.numberNode(number);
            case Float number -> JSON.numberNode(number);
            case Map<?, ?> map -> encodeMap(map);
            case Iterable<?> iterable -> encodeList(iterable);
            case Object[] array -> encodeList(Arrays.asList(array));
            default -> encodeAdapted(value);
        };
    }

    private ObjectNode encodeMap(Map<?, ?> map) {
        final var object = JSON.objectNode();
        for (final var entry : map.entrySet()) {
            object.set(String.valueOf(entry.getKey()), encode(entry.getValue()));
        }

        return object;
    }

    private ArrayNode encodeList(Iterable<?> values) {
        final var array = JSON.arrayNode();
        for (final var value : values) {
            array.add(encode(value));
        }

        return array;
    }

    private ObjectNode encodeAdapted(Object value) {
        final var adapter = adapters.find(value.getClass());
        if (adapter == null) {
            throw new IllegalArgumentException("No Rain adapter registered for " + value.getClass().getName()
                    + "; register one with adapters().register(...)");
        }

        final var writer = new ObjectWriter();
        adapter.write(value, writer);
        return writer.object;
    }

    private final class ObjectWriter implements PropertyWriter {
        private final ObjectNode object = JSON.objectNode();

        @Override
        public PropertyWriter put(String key, @Nullable Object value) {
            object.set(key, encode(value));
            return this;
        }

        @Override
        public PropertyWriter putItem(String key, @Nullable Object item) {
            object.set(key, item == null ? JSON.nullNode() : JSON.textNode(platform.encodeItem(item)));
            return this;
        }
    }
}
