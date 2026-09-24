package com.rainframework.ui.protocol;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public sealed interface TypeSchema
        permits TypeSchema.StringType, TypeSchema.IntType, TypeSchema.LongType, TypeSchema.DoubleType,
        TypeSchema.BoolType, TypeSchema.ItemType, TypeSchema.AssetType, TypeSchema.ListType, TypeSchema.ObjectType,
        TypeSchema.OptionalType {

    record StringType() implements TypeSchema {}

    record IntType() implements TypeSchema {}

    record LongType() implements TypeSchema {}

    record DoubleType() implements TypeSchema {}

    record BoolType() implements TypeSchema {}

    record ItemType() implements TypeSchema {}

    /** The value is the name of one of the screen's declared assets, never a hash or a URL. */
    record AssetType() implements TypeSchema {}

    record ListType(TypeSchema of) implements TypeSchema {}

    record ObjectType(Map<String, TypeSchema> fields) implements TypeSchema {}

    /**
     * In the contract JSON these are flags on the type itself ({@code "optional": true} or {@code "default": …}); the
     * parser wraps the type so code that only cares about the shape can {@link #unwrap} it. {@code defaultValue} is
     * null when there is no default. The validator, not the parser, checks that a default fits the type.
     */
    record OptionalType(TypeSchema inner, JsonNode defaultValue) implements TypeSchema {

        public boolean hasDefault() {
            return defaultValue != null;
        }
    }

    static TypeSchema unwrap(TypeSchema schema) {
        return schema instanceof OptionalType optional ? optional.inner() : schema;
    }

    /** Whether the value can be absent at runtime; a default counts as always present. */
    static boolean isOptional(TypeSchema schema) {
        return schema instanceof OptionalType optional && !optional.hasDefault();
    }
}
