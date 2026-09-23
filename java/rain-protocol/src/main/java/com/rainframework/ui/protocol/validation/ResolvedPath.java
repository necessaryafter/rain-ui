package com.rainframework.ui.protocol.validation;

import com.rainframework.ui.protocol.TypeSchema;

import java.util.Map;

/**
 * The type a dotted binding path ("bidData.currentBid") points to. {@code schema} is unwrapped, and {@code optional}
 * is true when any segment of the path may be absent at runtime.
 */
record ResolvedPath(TypeSchema schema, boolean optional) {

    static ResolvedPath resolve(Map<String, TypeSchema> scope, String path) {
        final var segments = path.split("\\.", -1);

        var schema = scope.get(segments[0]);
        if (schema == null) {
            return null;
        }

        var optional = TypeSchema.isOptional(schema);
        for (int i = 1; i < segments.length; i++) {
            if (!(TypeSchema.unwrap(schema) instanceof TypeSchema.ObjectType object)) {
                return null;
            }

            schema = object.fields().get(segments[i]);
            if (schema == null) {
                return null;
            }

            optional |= TypeSchema.isOptional(schema);
        }

        return new ResolvedPath(TypeSchema.unwrap(schema), optional);
    }

    /** The fields a list's item template can bind, or null when the source is not a list of objects. */
    static Map<String, TypeSchema> itemScopeOf(Map<String, TypeSchema> scope, String sourcePath) {
        final var source = resolve(scope, sourcePath);
        if (source == null || !(source.schema() instanceof TypeSchema.ListType list)) {
            return null;
        }

        if (!(TypeSchema.unwrap(list.of()) instanceof TypeSchema.ObjectType item)) {
            return null;
        }

        return item.fields();
    }
}
