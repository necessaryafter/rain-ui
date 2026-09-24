package com.rainframework.ui.protocol.validation;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainframework.ui.protocol.Limits;
import com.rainframework.ui.protocol.TypeSchema;
import lombok.RequiredArgsConstructor;

import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * Checks a JSON value from the wire against a declared type. Properties and payloads share it; {@code code} is the
 * mismatch code each reports, and {@code allowItems} is false for payloads, which the client builds and cannot carry
 * items.
 */
@RequiredArgsConstructor
final class ValueChecker {
    // Duplicate keys are rejected instead of letting the last one win, so what is validated is what gets read.
    static final ObjectMapper MAPPER = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    private final ValidationErrorCode code;
    private final boolean allowItems;
    private final Set<String> assetNames;

    ValidationResult checkFields(JsonNode object, Map<String, TypeSchema> fields, String path) {
        for (final var it = object.fieldNames(); it.hasNext(); ) {
            final var key = it.next();
            if (!fields.containsKey(key)) {
                return ValidationResult.fail(code, childPath(path, key));
            }
        }

        for (final var entry : fields.entrySet()) {
            final var result = check(object.get(entry.getKey()), entry.getValue(), childPath(path, entry.getKey()));
            if (!result.isValid()) {
                return result;
            }
        }

        return ValidationResult.ok();
    }

    // An optional value may be absent or null; a default counts as optional too, since the client fills it in.
    ValidationResult check(JsonNode value, TypeSchema schema, String path) {
        if (value == null || value.isNull()) {
            return schema instanceof TypeSchema.OptionalType ? ValidationResult.ok() : fail(path);
        }

        return switch (TypeSchema.unwrap(schema)) {
            case TypeSchema.StringType ignored -> checkString(value, path);
            case TypeSchema.IntType ignored -> matches(value.isIntegralNumber() && value.canConvertToInt(), path);
            case TypeSchema.LongType ignored -> matches(value.isIntegralNumber() && value.canConvertToLong(), path);
            case TypeSchema.DoubleType ignored -> matches(value.isNumber(), path);
            case TypeSchema.BoolType ignored -> matches(value.isBoolean(), path);
            case TypeSchema.ItemType ignored -> checkItem(value, path);
            case TypeSchema.AssetType ignored -> checkAsset(value, path);
            case TypeSchema.ListType list -> checkList(value, list.of(), path);
            case TypeSchema.ObjectType object -> value.isObject()
                    ? checkFields(value, object.fields(), path)
                    : fail(path);
            case TypeSchema.OptionalType ignored -> throw new IllegalStateException("unwrap returned an optional");
        };
    }

    ValidationResult fail(String path) {
        return ValidationResult.fail(code, path.isEmpty() ? "root" : path);
    }

    private ValidationResult checkString(JsonNode value, String path) {
        if (!value.isTextual()) {
            return fail(path);
        }

        if (value.asText().length() > Limits.MAX_PROPERTY_STRING_LENGTH) {
            return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, path);
        }

        return ValidationResult.ok();
    }

    // The size is checked on the base64 text, before decoding it.
    private ValidationResult checkItem(JsonNode value, String path) {
        if (!allowItems || !value.isTextual()) {
            return fail(path);
        }

        if (value.asText().length() > Limits.MAX_ITEM_BYTES) {
            return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, path);
        }

        try {
            Base64.getDecoder().decode(value.asText());
        } catch (IllegalArgumentException e) {
            return fail(path);
        }

        return ValidationResult.ok();
    }

    // The server names an asset; it never sends a hash or URL, so anything outside assetNames is refused.
    private ValidationResult checkAsset(JsonNode value, String path) {
        if (!value.isTextual()) {
            return fail(path);
        }

        if (!assetNames.contains(value.asText())) {
            return ValidationResult.fail(ValidationErrorCode.UNDECLARED_ASSET, path);
        }

        return ValidationResult.ok();
    }

    private ValidationResult checkList(JsonNode value, TypeSchema of, String path) {
        if (!value.isArray()) {
            return fail(path);
        }

        for (int i = 0; i < value.size(); i++) {
            final var result = check(value.get(i), of, path + "[" + i + "]");
            if (!result.isValid()) {
                return result;
            }
        }

        return ValidationResult.ok();
    }

    private ValidationResult matches(boolean matches, String path) {
        return matches ? ValidationResult.ok() : fail(path);
    }

    private static String childPath(String path, String key) {
        return path.isEmpty() ? key : path + "." + key;
    }
}
