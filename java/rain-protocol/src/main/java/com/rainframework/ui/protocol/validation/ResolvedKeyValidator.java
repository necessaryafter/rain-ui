package com.rainframework.ui.protocol.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.rainframework.ui.protocol.ComponentNode;
import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.TypeSchema;

import java.util.HashSet;
import java.util.Map;

final class ResolvedKeyValidator {
    ValidationResult validate(Contract contract, JsonNode properties) {
        return validateNode(contract.root(), properties, contract.properties(), "root");
    }

    private ValidationResult validateNode(
            ComponentNode node,
            JsonNode values,
            Map<String, TypeSchema> fields,
            String path
    ) {
        if (node.type().equals("list")) {
            return validateList(node, values, fields, path);
        }

        for (int i = 0; i < node.children().size(); i++) {
            final var result = validateNode(node.children().get(i), values, fields, path + ".children[" + i + "]");
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
            String path
    ) {
        if (node.children().isEmpty()) {
            return ValidationResult.ok();
        }

        final var source = node.props().get("source");
        if (!isBinding(source)) {
            return ValidationResult.ok();
        }

        final var sourcePath = source.get("$bind").asText();
        final var schema = ResolvedPath.resolve(fields, sourcePath);
        final var items = resolve(sourcePath, values, fields);
        if (schema == null || !(schema.schema() instanceof TypeSchema.ListType list)
                || !(TypeSchema.unwrap(list.of()) instanceof TypeSchema.ObjectType item)
                || items == null || !items.isArray()) {
            return ValidationResult.ok();
        }

        final var template = node.children().getFirst();
        final var key = template.key();
        final var seen = new HashSet<String>();
        final var templatePath = path + ".children[0]";

        for (final var value : items) {
            if (key != null) {
                final var resolved = isBinding(key)
                        ? resolve(key.get("$bind").asText(), value, item.fields())
                        : key;
                if (resolved == null || resolved.isNull()) {
                    return ValidationResult.fail(ValidationErrorCode.INVALID_KEY, templatePath + ".key");
                }

                final var identity = resolved.getNodeType() + ":" + resolved.asText();
                if (!seen.add(identity)) {
                    return ValidationResult.fail(ValidationErrorCode.DUPLICATE_KEY, templatePath + ".key");
                }
            }

            final var nested = validateNode(template, value, item.fields(), templatePath);
            if (!nested.isValid()) {
                return nested;
            }
        }

        return ValidationResult.ok();
    }

    private static JsonNode resolve(String path, JsonNode values, Map<String, TypeSchema> fields) {
        var current = values;
        var currentFields = fields;
        TypeSchema schema = null;

        for (final var segment : path.split("\\.")) {
            schema = currentFields.get(segment);
            if (schema == null) {
                return null;
            }

            current = current == null || current.isNull() ? null : current.get(segment);
            currentFields = TypeSchema.unwrap(schema) instanceof TypeSchema.ObjectType object
                    ? object.fields()
                    : Map.of();
        }

        if (current == null || current.isNull()) {
            return schema instanceof TypeSchema.OptionalType optional ? optional.defaultValue() : null;
        }

        return current;
    }

    private static boolean isBinding(JsonNode value) {
        return value != null && value.isObject() && value.size() == 1 && value.path("$bind").isTextual();
    }
}
