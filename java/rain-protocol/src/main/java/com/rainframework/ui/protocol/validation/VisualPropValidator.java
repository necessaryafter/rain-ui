package com.rainframework.ui.protocol.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.rainframework.ui.protocol.ComponentNode;
import com.rainframework.ui.protocol.TypeSchema;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class VisualPropValidator {
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#(?:[0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$");
    private static final Set<String> COLOR_PROPS = Set.of("color", "background", "borderColor", "strokeColor");
    private static final Set<String> NUMERIC_PROPS = Set.of(
            "minWidth", "minHeight", "maxWidth", "maxHeight", "gap", "top", "right", "bottom", "left",
            "grow", "shrink", "opacity", "zIndex", "borderWidth", "borderRadius", "rotate", "scale",
            "scaleX", "scaleY", "skewX", "skewY", "fontSize", "lineHeight", "letterSpacing", "strokeWidth");
    private static final Set<String> NONNEGATIVE_PROPS = Set.of(
            "minWidth", "minHeight", "maxWidth", "maxHeight", "gap", "grow", "shrink", "borderWidth",
            "borderRadius", "strokeWidth");
    private static final Set<String> POSITIVE_PROPS = Set.of("scale", "scaleX", "scaleY", "fontSize", "lineHeight");
    private static final Set<String> SPACING_SIDES = Set.of("top", "right", "bottom", "left");

    private VisualPropValidator() {
    }

    static ValidationResult validate(ComponentNode node, String path, Map<String, TypeSchema> scope) {
        final var props = node.props();

        for (final var entry : props.entrySet()) {
            final var propPath = path + ".props." + entry.getKey();
            final var result = validateProp(node.type(), entry.getKey(), entry.getValue(), propPath, scope);
            if (!result.isValid()) {
                return result;
            }
        }

        for (final var axis : new String[][] { { "minWidth", "maxWidth" }, { "minHeight", "maxHeight" } }) {
            final var min = props.get(axis[0]);
            final var max = props.get(axis[1]);
            if (isFiniteNumber(min) && isFiniteNumber(max) && min.asDouble() > max.asDouble()) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_LAYOUT, path + ".props." + axis[1]);
            }
        }

        for (final var axis : new String[][] { { "left", "right" }, { "top", "bottom" } }) {
            if (props.containsKey(axis[0]) && props.containsKey(axis[1])) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_COMBINATION, path + ".props." + axis[1]);
            }
        }

        return ValidationResult.ok();
    }

    private static ValidationResult validateProp(
            String type,
            String key,
            JsonNode value,
            String path,
            Map<String, TypeSchema> scope
    ) {
        if (key.equals("width") || key.equals("height")) {
            if (value != null && value.isTextual()
                    && (value.asText().equals("fit") || value.asText().equals("fill"))) {
                return ValidationResult.ok();
            }

            final var numeric = validateNumeric(value, path, scope);
            if (!numeric.isValid()) {
                return numeric;
            }

            return value.isNumber() && value.asDouble() < 0
                    ? ValidationResult.fail(ValidationErrorCode.INVALID_PROP_VALUE, path)
                    : ValidationResult.ok();
        }

        if (key.equals("padding") || key.equals("margin")) {
            return validSpacing(value) ? ValidationResult.ok()
                    : ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
        }

        if (COLOR_PROPS.contains(key)) {
            if (value != null && value.isTextual()) {
                return COLOR_PATTERN.matcher(value.asText()).matches()
                        ? ValidationResult.ok()
                        : ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
            }

            return validateBinding(value, path, scope, false);
        }

        if (key.equals("align")) {
            final var allowed = type.equals("text")
                    ? Set.of("left", "center", "right", "start", "end")
                    : Set.of("start", "center", "end", "stretch");

            return validEnum(value, allowed, path);
        }

        if (key.equals("justify")) {
            return validEnum(value, Set.of("start", "center", "end", "space-between"), path);
        }

        if (key.equals("fontWeight")) {
            return validEnum(value, Set.of("normal", "bold"), path);
        }

        if (key.equals("textAlign")) {
            return validEnum(value, Set.of("start", "center", "end"), path);
        }

        if (key.equals("position")) {
            return validEnum(value, Set.of("relative", "absolute"), path);
        }

        if (key.equals("overflow")) {
            return validEnum(value, Set.of("visible", "hidden"), path);
        }

        if (key.equals("shadow") || key.equals("multiline")) {
            return value != null && value.isBoolean() ? ValidationResult.ok()
                    : ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
        }

        if (key.equals("size") && type.equals("item")) {
            return value != null && value.isIntegralNumber() && value.asLong() > 0 && value.canConvertToInt()
                    ? ValidationResult.ok()
                    : ValidationResult.fail(ValidationErrorCode.INVALID_PROP_VALUE, path);
        }

        if (type.equals("grid") && (key.equals("cellWidth") || key.equals("cellHeight")
                || key.equals("columns"))) {
            if (value != null && value.isIntegralNumber() && value.asLong() > 0 && value.canConvertToInt()) {
                return ValidationResult.ok();
            }

            if (isBinding(value)) {
                return validateBinding(value, path, scope, true, true);
            }

            return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_VALUE, path);
        }

        if (!NUMERIC_PROPS.contains(key)) {
            return ValidationResult.ok();
        }

        final var numeric = validateNumeric(value, path, scope);
        if (!numeric.isValid()) {
            return numeric;
        }

        if (value != null && value.isNumber()) {
            final var number = value.asDouble();
            if (NONNEGATIVE_PROPS.contains(key) && number < 0
                    || POSITIVE_PROPS.contains(key) && number <= 0
                    || key.equals("opacity") && (number < 0 || number > 1)
                    || key.equals("zIndex") && (!value.isIntegralNumber() || !value.canConvertToInt())) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_VALUE, path);
            }
        }

        return ValidationResult.ok();
    }

    private static ValidationResult validEnum(JsonNode value, Set<String> allowed, String path) {
        return value != null && value.isTextual() && allowed.contains(value.asText())
                ? ValidationResult.ok()
                : ValidationResult.fail(ValidationErrorCode.INVALID_PROP_VALUE, path);
    }

    private static ValidationResult validateNumeric(JsonNode value, String path, Map<String, TypeSchema> scope) {
        if (isFiniteNumber(value)) {
            return ValidationResult.ok();
        }

        if (value != null && value.isNumber()) {
            return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
        }

        return validateBinding(value, path, scope, true);
    }

    private static ValidationResult validateBinding(
            JsonNode value,
            String path,
            Map<String, TypeSchema> scope,
            boolean numeric
    ) {
        return validateBinding(value, path, scope, numeric, false);
    }

    private static ValidationResult validateBinding(
            JsonNode value,
            String path,
            Map<String, TypeSchema> scope,
            boolean numeric,
            boolean integerOnly
    ) {
        if (value == null || !value.isObject() || value.size() != 1 || !value.path("$bind").isTextual()) {
            return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
        }

        final var resolved = ResolvedPath.resolve(scope, value.get("$bind").asText());
        if (resolved == null) {
            return ValidationResult.fail(ValidationErrorCode.UNDECLARED_BINDING, path);
        }

        final var schema = resolved.schema();
        final var validType = numeric
                ? schema instanceof TypeSchema.IntType || schema instanceof TypeSchema.LongType
                        || !integerOnly && schema instanceof TypeSchema.DoubleType
                : schema instanceof TypeSchema.StringType;
        if (!validType) {
            return ValidationResult.fail(ValidationErrorCode.BINDING_TYPE_MISMATCH, path);
        }

        return ValidationResult.ok();
    }

    private static boolean validSpacing(JsonNode value) {
        if (isFiniteNumber(value)) {
            return value.asDouble() >= 0;
        }

        if (value == null || !value.isObject()) {
            return false;
        }

        for (final var entry : value.properties()) {
            if (!SPACING_SIDES.contains(entry.getKey()) || !isFiniteNumber(entry.getValue())
                    || entry.getValue().asDouble() < 0) {
                return false;
            }
        }

        return true;
    }

    private static boolean isFiniteNumber(JsonNode value) {
        return value != null && value.isNumber() && Double.isFinite(value.asDouble());
    }

    private static boolean isBinding(JsonNode value) {
        return value != null && value.isObject() && value.size() == 1 && value.path("$bind").isTextual();
    }
}
