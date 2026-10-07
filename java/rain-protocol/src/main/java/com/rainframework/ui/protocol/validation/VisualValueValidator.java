package com.rainframework.ui.protocol.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.rainframework.ui.protocol.ComponentNode;
import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.TypeSchema;

import java.util.HashMap;
import java.util.Map;

public final class VisualValueValidator {
    public ValidationResult validate(Contract contract, JsonNode properties) {
        return validateNode(contract.root(), properties, contract.properties(), "root", "properties");
    }

    private ValidationResult validateNode(
            ComponentNode node,
            JsonNode values,
            Map<String, TypeSchema> fields,
            String path,
            String valuePath
    ) {
        final var resolvedProps = new HashMap<>(node.props());
        final var boundPaths = new HashMap<String, String>();

        for (final var entry : node.props().entrySet()) {
            if (!isBinding(entry.getValue())) {
                continue;
            }

            final var resolved = resolve(entry.getValue().get("$bind").asText(), values, fields, valuePath);
            if (resolved == null || resolved.value() == null || resolved.value().isNull()) {
                resolvedProps.remove(entry.getKey());
                continue;
            }

            resolvedProps.put(entry.getKey(), resolved.value());
            boundPaths.put(entry.getKey(), resolved.path());
        }

        final var resolvedNode = new ComponentNode(node.type(), resolvedProps, node.children(), node.key());
        final var visual = VisualPropValidator.validate(resolvedNode, path, Map.of());
        if (!visual.isValid()) {
            final var propPath = visual.getError().getPath();
            final var key = propPath.substring(propPath.lastIndexOf('.') + 1);
            final var offendingPath = boundPaths.getOrDefault(key,
                    boundPaths.getOrDefault(key.startsWith("max") ? "min" + key.substring(3) : key, valuePath));

            return ValidationResult.fail(ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH, offendingPath);
        }

        if (node.type().equals("list")) {
            return validateList(node, values, fields, path, valuePath);
        }

        for (int i = 0; i < node.children().size(); i++) {
            final var child = node.children().get(i);
            final var result = validateNode(child, values, fields, path + ".children[" + i + "]", valuePath);
            if (!result.isValid()) {
                return result;
            }
        }

        return ValidationResult.ok();
    }

    private ValidationResult validateList(
            ComponentNode node,
            JsonNode values,
            Map<String, TypeSchema> fields,
            String path,
            String valuePath
    ) {
        final var source = node.props().get("source");
        if (!isBinding(source)) {
            return ValidationResult.ok();
        }

        final var sourcePath = source.get("$bind").asText();
        final var resolved = resolve(sourcePath, values, fields, valuePath);
        final var schema = ResolvedPath.resolve(fields, sourcePath);
        if (resolved == null || resolved.value() == null || !resolved.value().isArray()
                || schema == null || !(schema.schema() instanceof TypeSchema.ListType list)
                || !(TypeSchema.unwrap(list.of()) instanceof TypeSchema.ObjectType item)) {
            return ValidationResult.ok();
        }

        for (int itemIndex = 0; itemIndex < resolved.value().size(); itemIndex++) {
            for (int childIndex = 0; childIndex < node.children().size(); childIndex++) {
                final var result = validateNode(
                        node.children().get(childIndex), resolved.value().get(itemIndex), item.fields(),
                        path + ".children[" + childIndex + "]", resolved.path() + "[" + itemIndex + "]");
                if (!result.isValid()) {
                    return result;
                }
            }
        }

        return ValidationResult.ok();
    }

    private static ValueAt resolve(
            String binding,
            JsonNode values,
            Map<String, TypeSchema> fields,
            String valuePath
    ) {
        var current = values;
        var currentFields = fields;
        TypeSchema schema = null;
        var path = valuePath;

        for (final var segment : binding.split("\\.")) {
            schema = currentFields.get(segment);
            if (schema == null) {
                return null;
            }

            current = current == null || current.isNull() ? null : current.get(segment);
            currentFields = TypeSchema.unwrap(schema) instanceof TypeSchema.ObjectType object
                    ? object.fields()
                    : Map.of();
            path += "." + segment;
        }

        if (current == null || current.isNull()) {
            current = schema instanceof TypeSchema.OptionalType optional ? optional.defaultValue() : null;
        }

        return new ValueAt(current, path);
    }

    private static boolean isBinding(JsonNode value) {
        return value != null && value.isObject() && value.size() == 1 && value.path("$bind").isTextual();
    }

    private record ValueAt(JsonNode value, String path) {
    }
}
