package com.rainframework.ui.protocol.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.rainframework.ui.protocol.AssetInfo;
import com.rainframework.ui.protocol.ComponentNode;
import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.Limits;
import com.rainframework.ui.protocol.TypeSchema;
import lombok.RequiredArgsConstructor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class ContractValidator {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_-]+:[a-z0-9_/-]+$");
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/gif");
    private static final Set<String> FONT_TYPES = Set.of("font/ttf", "font/otf");
    private static final Map<String, Set<String>> COMPONENT_PROPS = Map.ofEntries(
            Map.entry("column", Set.of("gap", "padding", "align", "justify", "width", "height")),
            Map.entry("row", Set.of("gap", "padding", "align", "justify", "width", "height")),
            Map.entry("text", Set.of("value", "color", "font", "align", "shadow")),
            Map.entry("image", Set.of("src", "width", "height")),
            Map.entry("item", Set.of("value", "size")),
            Map.entry("button", Set.of("action", "payload", "disabled")),
            Map.entry("list", Set.of("source")),
            Map.entry("show", Set.of("when")),
            Map.entry("match", Set.of("value")),
            Map.entry("case", Set.of("is")),
            Map.entry("default", Set.of()),
            Map.entry("fallback", Set.of()));

    // Components that only exist as a slot of a specific parent.
    private static final Map<String, String> SLOT_PARENTS = Map.of(
            "case", "match",
            "default", "match",
            "fallback", "show");

    public ValidationResult validate(Contract contract) {
        final var basicChecks = validateBasicConstraints(contract);
        if (!basicChecks.isValid()) {
            return basicChecks;
        }

        final var propertiesCheck = validateSchemas(contract.properties(), "properties", true);
        if (!propertiesCheck.isValid()) {
            return propertiesCheck;
        }

        final var actionsCheck = validateSchemas(contract.actions(), "actions", false);
        if (!actionsCheck.isValid()) {
            return actionsCheck;
        }

        final var assetsCheck = validateAssets(contract.assets(), contract.assetNames());
        if (!assetsCheck.isValid()) {
            return assetsCheck;
        }

        final var validator = new NodeValidator(contract.actions(), contract.assets());
        return validator.validateNode(contract.root(), "root", contract.properties(), 0, null);
    }

    // Table-wide checks come first, so a malformed or oversized table is reported as a whole before any single entry.
    private static ValidationResult validateAssets(Map<String, AssetInfo> assets, Map<String, String> assetNames) {
        if (!assets.keySet().stream().allMatch(hash -> HASH_PATTERN.matcher(hash).matches())) {
            return ValidationResult.fail(ValidationErrorCode.INVALID_ASSET_HASH, "assets");
        }

        if (assets.size() > Limits.MAX_ASSETS) {
            return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, "assets");
        }

        final var totalBytes = assets.values().stream()
                .mapToLong(info -> info.bytes() == null ? 0 : info.bytes())
                .sum();
        if (totalBytes > Limits.MAX_TOTAL_ASSET_BYTES) {
            return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, "assets");
        }

        for (final var entry : assets.entrySet()) {
            final var result = validateAssetInfo(entry.getValue(), "assets." + entry.getKey());
            if (!result.isValid()) {
                return result;
            }
        }

        for (final var entry : assetNames.entrySet()) {
            if (entry.getValue() == null || !assets.containsKey(entry.getValue())) {
                return ValidationResult.fail(ValidationErrorCode.UNDECLARED_ASSET, "assetNames." + entry.getKey());
            }
        }

        return ValidationResult.ok();
    }

    private static ValidationResult validateAssetInfo(AssetInfo info, String path) {
        // Set.of rejects contains(null), so a missing type is checked before the lookups.
        final var type = info.type();
        if (type == null || !isCount(info.bytes())) {
            return ValidationResult.fail(ValidationErrorCode.UNSUPPORTED_ASSET, path);
        }

        final var isImage = IMAGE_TYPES.contains(type);
        if (!isImage && !FONT_TYPES.contains(type)) {
            return ValidationResult.fail(ValidationErrorCode.UNSUPPORTED_ASSET, path);
        }

        if (info.bytes() > Limits.MAX_ASSET_BYTES) {
            return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, path);
        }

        if (!isImage) {
            return ValidationResult.ok();
        }

        if (!isPositive(info.width()) || !isPositive(info.height())) {
            return ValidationResult.fail(ValidationErrorCode.UNSUPPORTED_ASSET, path);
        }

        if (info.width() > Limits.MAX_IMAGE_DIMENSION || info.height() > Limits.MAX_IMAGE_DIMENSION) {
            return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, path);
        }

        if (!type.equals("image/gif")) {
            return ValidationResult.ok();
        }

        if (!isPositive(info.frames())) {
            return ValidationResult.fail(ValidationErrorCode.UNSUPPORTED_ASSET, path);
        }

        final var decodedBytes = info.width() * info.height() * 4 * info.frames();
        if (info.frames() > Limits.MAX_GIF_FRAMES || decodedBytes > Limits.MAX_GIF_DECODED_BYTES) {
            return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, path);
        }

        return ValidationResult.ok();
    }

    private static boolean isCount(Long value) {
        return value != null && value >= 0;
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    private static boolean isSize(JsonNode value) {
        return value.isNumber() || value.isTextual() && (value.asText().equals("fit") || value.asText().equals("fill"));
    }

    private static boolean isAssetRef(JsonNode node) {
        return node != null && node.isObject() && node.size() == 1 && node.path("$asset").isTextual();
    }

    private ValidationResult validateBasicConstraints(Contract contract) {
        if (contract.sourceBytes() > Limits.MAX_CONTRACT_BYTES) {
            return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, "root");
        }

        if (!ID_PATTERN.matcher(contract.id()).matches()) {
            return ValidationResult.fail(ValidationErrorCode.INVALID_ID, "id");
        }

        if (contract.actions().size() > Limits.MAX_ACTIONS) {
            return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, "actions");
        }

        for (final var actionId : contract.actions().keySet()) {
            if (!ID_PATTERN.matcher(actionId).matches()) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_ID, "actions");
            }
        }

        return ValidationResult.ok();
    }

    private static ValidationResult validateSchemas(
            Map<String, TypeSchema> schemas,
            String path,
            boolean allowDefault
    ) {
        for (final var entry : schemas.entrySet()) {
            final var result = validateSchema(entry.getValue(), path + "." + entry.getKey(), allowDefault);
            if (!result.isValid()) {
                return result;
            }
        }

        return ValidationResult.ok();
    }

    private static ValidationResult validateSchema(TypeSchema schema, String path, boolean allowDefault) {
        if (schema instanceof TypeSchema.OptionalType optional && optional.hasDefault()) {
            final var defaultCheck = validateDefault(optional, path, allowDefault);
            if (!defaultCheck.isValid()) {
                return defaultCheck;
            }
        }

        final var shape = TypeSchema.unwrap(schema);
        if (shape instanceof TypeSchema.ListType list) {
            return validateSchema(list.of(), path + ".of", allowDefault);
        }

        if (shape instanceof TypeSchema.ObjectType object) {
            return validateSchemas(object.fields(), path + ".fields", allowDefault);
        }

        return ValidationResult.ok();
    }

    // Defaults are only meaningful for properties the server sends; an action payload comes from the client.
    private static ValidationResult validateDefault(
            TypeSchema.OptionalType optional,
            String path,
            boolean allowDefault
    ) {
        final var value = optional.defaultValue();
        if (!allowDefault || !isScalar(optional.inner()) || !matchesKind(value, optional.inner())) {
            return ValidationResult.fail(ValidationErrorCode.INVALID_DEFAULT, path);
        }

        if (value.isTextual() && value.asText().length() > Limits.MAX_STRING_LENGTH) {
            return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, path);
        }

        return ValidationResult.ok();
    }

    private static boolean isScalar(TypeSchema schema) {
        return schema instanceof TypeSchema.StringType
                || schema instanceof TypeSchema.IntType
                || schema instanceof TypeSchema.LongType
                || schema instanceof TypeSchema.DoubleType
                || schema instanceof TypeSchema.BoolType;
    }

    private static boolean isMatchable(TypeSchema schema) {
        return isScalar(schema) && !(schema instanceof TypeSchema.DoubleType);
    }

    private static boolean matchesKind(JsonNode value, TypeSchema schema) {
        return switch (schema) {
            case TypeSchema.StringType ignored -> value.isTextual();
            case TypeSchema.IntType ignored -> value.isIntegralNumber();
            case TypeSchema.LongType ignored -> value.isIntegralNumber();
            case TypeSchema.DoubleType ignored -> value.isNumber();
            case TypeSchema.BoolType ignored -> value.isBoolean();
            default -> false;
        };
    }

    private static boolean isBinding(JsonNode node) {
        return node != null && node.isObject() && node.size() == 1 && node.path("$bind").isTextual();
    }

    @RequiredArgsConstructor
    private static class NodeValidator {
        private final Map<String, TypeSchema> actions;
        private final Map<String, AssetInfo> assets;
        private int nodeCount = 0;

        ValidationResult validateNode(
                ComponentNode node,
                String path,
                Map<String, TypeSchema> scope,
                int depth,
                String parentType
        ) {
            nodeCount++;
            if (nodeCount > Limits.MAX_NODES || depth > Limits.MAX_DEPTH) {
                return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, path);
            }

            if (!COMPONENT_PROPS.containsKey(node.type())) {
                return ValidationResult.fail(ValidationErrorCode.UNKNOWN_COMPONENT, path);
            }

            final var slotParent = SLOT_PARENTS.get(node.type());
            if (slotParent != null && !slotParent.equals(parentType)) {
                return ValidationResult.fail(ValidationErrorCode.MISPLACED_COMPONENT, path);
            }

            final var propsCheck = validateProps(node, path, scope);
            if (!propsCheck.isValid()) {
                return propsCheck;
            }

            return validateChildren(node, path, scope, depth);
        }

        private ValidationResult validateProps(ComponentNode node, String path, Map<String, TypeSchema> scope) {
            final var allowed = COMPONENT_PROPS.get(node.type());

            for (final var entry : node.props().entrySet()) {
                final var propPath = path + ".props." + entry.getKey();
                if (!allowed.contains(entry.getKey())) {
                    return ValidationResult.fail(ValidationErrorCode.UNKNOWN_PROP, propPath);
                }

                final var result = validateProp(node.type() + "." + entry.getKey(), entry.getValue(), propPath, scope);
                if (!result.isValid()) {
                    return result;
                }
            }

            if (node.type().equals("button") && node.props().containsKey("payload")) {
                return validateButtonPayload(node, path + ".props.payload", scope);
            }

            return ValidationResult.ok();
        }

        private ValidationResult validateProp(String prop, JsonNode value, String path, Map<String, TypeSchema> scope) {
            return switch (prop) {
                case "text.value" -> validateTextValue(value, path, scope);
                case "text.color" -> validateColor(value, path, scope);
                case "text.font" -> validateAssetRef(value, path, FONT_TYPES);
                case "image.src" -> isBinding(value)
                        ? validateBinding(value, path, scope, TypeSchema.AssetType.class::isInstance)
                        : validateAssetRef(value, path, IMAGE_TYPES);
                case "image.width", "image.height" -> isSize(value)
                        ? ValidationResult.ok()
                        : ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
                case "column.gap", "column.padding", "row.gap", "row.padding" -> value.isNumber()
                        ? ValidationResult.ok()
                        : ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
                case "item.value" -> validateBinding(value, path, scope, TypeSchema.ItemType.class::isInstance);
                case "button.action" -> validateAction(value, path);
                case "button.disabled" -> value.isBoolean()
                        ? ValidationResult.ok()
                        : validateBinding(value, path, scope, TypeSchema.BoolType.class::isInstance);
                case "list.source" -> validateBinding(value, path, scope, TypeSchema.ListType.class::isInstance);
                case "show.when" -> validateBinding(value, path, scope, schema -> true);
                case "match.value" -> validateBinding(value, path, scope, ContractValidator::isMatchable);
                default -> ValidationResult.ok();
            };
        }

        private ValidationResult validateAssetRef(JsonNode value, String path, Set<String> acceptedTypes) {
            if (!isAssetRef(value)) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
            }

            final var info = assets.get(value.get("$asset").asText());
            if (info == null) {
                return ValidationResult.fail(ValidationErrorCode.UNDECLARED_ASSET, path);
            }

            if (info.type() == null || !acceptedTypes.contains(info.type())) {
                return ValidationResult.fail(ValidationErrorCode.ASSET_TYPE_MISMATCH, path);
            }

            return ValidationResult.ok();
        }

        private ValidationResult validateAction(JsonNode value, String path) {
            if (!value.isTextual()) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
            }

            if (!actions.containsKey(value.asText())) {
                return ValidationResult.fail(ValidationErrorCode.UNDECLARED_ACTION, path);
            }

            return ValidationResult.ok();
        }

        private ValidationResult validateChildren(
                ComponentNode node,
                String path,
                Map<String, TypeSchema> scope,
                int depth
        ) {
            final var children = node.children();

            if (node.type().equals("match")) {
                final var slotsCheck = validateMatchSlots(node, children, path, scope);
                if (!slotsCheck.isValid()) {
                    return slotsCheck;
                }
            }

            for (int i = 0; i < children.size(); i++) {
                final var child = children.get(i);
                final var childPath = path + ".children[" + i + "]";

                if (child.type().equals("fallback") && i != children.size() - 1) {
                    return ValidationResult.fail(ValidationErrorCode.MISPLACED_COMPONENT, childPath);
                }

                final var childScope = node.type().equals("list") && i == 0 ? listItemScope(node, scope) : scope;
                final var result = validateNode(child, childPath, childScope, depth + 1, node.type());
                if (!result.isValid()) {
                    return result;
                }
            }

            return ValidationResult.ok();
        }

        private ValidationResult validateButtonPayload(ComponentNode node, String path, Map<String, TypeSchema> scope) {
            final var action = node.props().get("action");
            if (action == null || !action.isTextual() || !actions.containsKey(action.asText())) {
                return ValidationResult.fail(ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH, path);
            }

            return validatePayloadValue(node.props().get("payload"), actions.get(action.asText()), path, scope);
        }
    }

    // Checks what only the match itself knows: children are cases or one trailing default, and each case literal has
    // the type of the matched value and appears once.
    private static ValidationResult validateMatchSlots(
            ComponentNode node,
            List<ComponentNode> children,
            String path,
            Map<String, TypeSchema> scope
    ) {
        final var value = node.props().get("value");
        final var matched = isBinding(value) ? ResolvedPath.resolve(scope, value.get("$bind").asText()) : null;
        final var seen = new HashSet<JsonNode>();

        for (int i = 0; i < children.size(); i++) {
            final var child = children.get(i);
            final var childPath = path + ".children[" + i + "]";

            if (child.type().equals("default")) {
                if (i != children.size() - 1) {
                    return ValidationResult.fail(ValidationErrorCode.MISPLACED_COMPONENT, childPath);
                }
                continue;
            }

            if (!child.type().equals("case")) {
                return ValidationResult.fail(ValidationErrorCode.MISPLACED_COMPONENT, childPath);
            }

            final var literal = child.props().get("is");
            final var literalPath = childPath + ".props.is";
            if (literal == null || matched != null && !matchesKind(literal, matched.schema())) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, literalPath);
            }

            if (!seen.add(literal)) {
                return ValidationResult.fail(ValidationErrorCode.DUPLICATE_CASE, literalPath);
            }
        }

        return ValidationResult.ok();
    }

    private static ValidationResult validateTextValue(JsonNode value, String path, Map<String, TypeSchema> scope) {
        if (!value.isTextual()) {
            return validateBinding(value, path, scope, schema -> schema instanceof TypeSchema.StringType
                    || schema instanceof TypeSchema.IntType
                    || schema instanceof TypeSchema.LongType);
        }

        if (value.asText().length() > Limits.MAX_STRING_LENGTH) {
            return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, path);
        }

        return ValidationResult.ok();
    }

    // A bad color that arrives at runtime through a binding falls back to the default color on the client.
    private static ValidationResult validateColor(JsonNode value, String path, Map<String, TypeSchema> scope) {
        if (!value.isTextual()) {
            return validateBinding(value, path, scope, TypeSchema.StringType.class::isInstance);
        }

        if (!COLOR_PATTERN.matcher(value.asText()).matches()) {
            return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
        }

        return ValidationResult.ok();
    }

    private static ValidationResult validateBinding(
            JsonNode value,
            String path,
            Map<String, TypeSchema> scope,
            Predicate<TypeSchema> accepts
    ) {
        if (!isBinding(value)) {
            return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
        }

        final var resolved = ResolvedPath.resolve(scope, value.get("$bind").asText());
        if (resolved == null) {
            return ValidationResult.fail(ValidationErrorCode.UNDECLARED_BINDING, path);
        }

        if (!accepts.test(resolved.schema())) {
            return ValidationResult.fail(ValidationErrorCode.BINDING_TYPE_MISMATCH, path);
        }

        return ValidationResult.ok();
    }

    // An unresolvable source is reported by the list's own prop check; the template then falls back to the outer scope.
    private static Map<String, TypeSchema> listItemScope(ComponentNode node, Map<String, TypeSchema> scope) {
        final var source = node.props().get("source");
        if (!isBinding(source)) {
            return scope;
        }

        final var itemScope = ResolvedPath.itemScopeOf(scope, source.get("$bind").asText());
        return itemScope == null ? scope : itemScope;
    }

    private static ValidationResult validatePayloadValue(
            JsonNode value,
            TypeSchema schema,
            String path,
            Map<String, TypeSchema> scope
    ) {
        if (isBinding(value)) {
            final var resolved = ResolvedPath.resolve(scope, value.get("$bind").asText());
            if (resolved == null) {
                return ValidationResult.fail(ValidationErrorCode.UNDECLARED_BINDING, path);
            }

            if (!isAssignable(resolved, schema)) {
                return ValidationResult.fail(ValidationErrorCode.BINDING_TYPE_MISMATCH, path);
            }

            return ValidationResult.ok();
        }

        if (value == null || value.isNull()) {
            return schema instanceof TypeSchema.OptionalType
                    ? ValidationResult.ok()
                    : ValidationResult.fail(ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH, path);
        }

        return switch (TypeSchema.unwrap(schema)) {
            case TypeSchema.ObjectType object -> validatePayloadObject(value, object.fields(), path, scope);
            case TypeSchema.ListType list -> validatePayloadList(value, list.of(), path, scope);
            case TypeSchema shape -> matchesKind(value, shape)
                    ? ValidationResult.ok()
                    : ValidationResult.fail(ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH, path);
        };
    }

    private static ValidationResult validatePayloadObject(
            JsonNode value,
            Map<String, TypeSchema> fields,
            String path,
            Map<String, TypeSchema> scope
    ) {
        if (!value.isObject()) {
            return ValidationResult.fail(ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH, path);
        }

        for (final var entry : fields.entrySet()) {
            if (!value.has(entry.getKey()) && !(entry.getValue() instanceof TypeSchema.OptionalType)) {
                return ValidationResult.fail(ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH, path + "." + entry.getKey());
            }
        }

        for (final var it = value.fields(); it.hasNext(); ) {
            final var entry = it.next();
            final var fieldPath = path + "." + entry.getKey();

            final var field = fields.get(entry.getKey());
            if (field == null) {
                return ValidationResult.fail(ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH, fieldPath);
            }

            final var result = validatePayloadValue(entry.getValue(), field, fieldPath, scope);
            if (!result.isValid()) {
                return result;
            }
        }

        return ValidationResult.ok();
    }

    private static ValidationResult validatePayloadList(
            JsonNode value,
            TypeSchema of,
            String path,
            Map<String, TypeSchema> scope
    ) {
        if (!value.isArray()) {
            return ValidationResult.fail(ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH, path);
        }

        for (int i = 0; i < value.size(); i++) {
            final var result = validatePayloadValue(value.get(i), of, path + "[" + i + "]", scope);
            if (!result.isValid()) {
                return result;
            }
        }

        return ValidationResult.ok();
    }

    // A bound value fits a payload field when the shapes match and, if the field is required, the value is always
    // present.
    private static boolean isAssignable(ResolvedPath bound, TypeSchema target) {
        if (!sameShape(bound.schema(), TypeSchema.unwrap(target))) {
            return false;
        }

        return !bound.optional() || target instanceof TypeSchema.OptionalType;
    }

    private static boolean sameShape(TypeSchema a, TypeSchema b) {
        if (a instanceof TypeSchema.ListType aList && b instanceof TypeSchema.ListType bList) {
            return sameShape(TypeSchema.unwrap(aList.of()), TypeSchema.unwrap(bList.of()));
        }

        if (a instanceof TypeSchema.ObjectType aObject && b instanceof TypeSchema.ObjectType bObject) {
            if (!aObject.fields().keySet().equals(bObject.fields().keySet())) {
                return false;
            }

            return aObject.fields().keySet().stream().allMatch(key -> sameShape(
                    TypeSchema.unwrap(aObject.fields().get(key)),
                    TypeSchema.unwrap(bObject.fields().get(key))));
        }

        return a.getClass() == b.getClass();
    }
}
