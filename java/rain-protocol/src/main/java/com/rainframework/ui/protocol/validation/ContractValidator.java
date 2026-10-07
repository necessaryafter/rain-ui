package com.rainframework.ui.protocol.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.rainframework.ui.protocol.AssetInfo;
import com.rainframework.ui.protocol.ComponentNode;
import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.Limits;
import com.rainframework.ui.protocol.TypeSchema;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class ContractValidator {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_-]+:[a-z0-9_/-]+$");
    private static final Pattern COLOR_PATTERN = Pattern.compile("^#(?:[0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$");
    private static final Pattern HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/gif");
    private static final Set<String> FONT_TYPES = Set.of("font/ttf", "font/otf");
    private static final Map<String, Set<String>> COMPONENT_PROPS = Map.ofEntries(
            Map.entry("column", layoutProps()),
            Map.entry("row", layoutProps()),
            Map.entry("box", layoutProps()),
            Map.entry("stack", layoutProps()),
            Map.entry("grid", withLayout("cellWidth", "cellHeight", "columns")),
            Map.entry("scroll", withLayout("direction")),
            Map.entry("text", Set.of(
                    "value", "color", "font", "align", "shadow", "fontSize", "fontWeight", "textAlign",
                    "lineHeight", "letterSpacing", "strokeColor", "strokeWidth")),
            Map.entry("image", withLayout("src")),
            Map.entry("item", Set.of("value", "size")),
            Map.entry("button", withLayout("action", "payload", "disabled")),
            Map.entry("input", Set.of(
                    "id", "value", "placeholder", "multiline", "maxLength", "width", "height", "minWidth",
                    "minHeight", "maxWidth", "maxHeight", "margin", "position", "top", "right", "bottom", "left")),
            Map.entry("tabs", Set.of("defaultTab")),
            Map.entry("tab", Set.of("id", "label")),
            Map.entry("list", Set.of("source")),
            Map.entry("show", Set.of("when")),
            Map.entry("match", Set.of("value")),
            Map.entry("case", Set.of("is")),
            Map.entry("default", Set.of()),
            Map.entry("fallback", Set.of()));

    private static Set<String> layoutProps() {
        return Set.of(
                "width", "height", "minWidth", "minHeight", "maxWidth", "maxHeight", "padding", "margin", "gap",
                "position", "top", "right", "bottom", "left", "align", "justify", "grow", "shrink", "background",
                "opacity", "overflow", "zIndex", "borderWidth", "borderColor", "borderRadius", "rotate", "scale",
                "scaleX", "scaleY", "skewX", "skewY", "shape");
    }

    private static Set<String> withLayout(String... extra) {
        final var props = new HashSet<>(layoutProps());
        Collections.addAll(props, extra);

        return Set.copyOf(props);
    }

    // Components that only exist as a slot of a specific parent.
    private static final Map<String, String> SLOT_PARENTS = Map.of(
            "case", "match",
            "default", "match",
            "fallback", "show",
            "tab", "tabs");

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

        final var screenInputs = collectInputIds(contract.root());
        final var validator = new NodeValidator(contract.actions(), contract.assets(), screenInputs);

        return validator.validateNode(contract.root(), "root", contract.properties(), 0, null, screenInputs);
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

    private static boolean isInputReference(JsonNode node) {
        return node != null && node.isObject() && node.size() == 1 && node.path("$input").isTextual();
    }

    private static boolean positiveInteger(JsonNode value) {
        return value != null && value.isIntegralNumber() && value.asLong() > 0;
    }

    private static boolean nonZero(JsonNode value) {
        return value != null && value.isNumber() && value.asDouble() != 0;
    }

    private static boolean validPolygon(JsonNode value) {
        if (value == null || !value.isArray() || value.size() < 3 || value.size() > 32) {
            return false;
        }

        long area = 0;

        for (int i = 0; i < value.size(); i++) {
            final var point = value.get(i);
            final var next = value.get((i + 1) % value.size());
            if (!validPolygonPoint(point) || !validPolygonPoint(next)) {
                return false;
            }

            if (point.get(0).asInt() == next.get(0).asInt()
                    && point.get(1).asInt() == next.get(1).asInt()) {
                return false;
            }

            area += (long) point.get(0).asInt() * next.get(1).asInt()
                    - (long) next.get(0).asInt() * point.get(1).asInt();
        }

        if (area == 0) {
            return false;
        }

        for (int i = 0; i < value.size(); i++) {
            if (crossesLaterSegment(value, i)) {
                return false;
            }
        }

        return true;
    }

    private static boolean validPolygonPoint(JsonNode point) {
        if (!point.isArray() || point.size() != 2) {
            return false;
        }

        for (final var coordinate : point) {
            if (!coordinate.isIntegralNumber() || !coordinate.canConvertToInt()
                    || coordinate.asInt() < 0 || coordinate.asInt() > 10000) {
                return false;
            }
        }

        return true;
    }

    private static boolean crossesLaterSegment(JsonNode points, int i) {
        for (int j = i + 1; j < points.size(); j++) {
            if (j == i + 1 || (i == 0 && j == points.size() - 1)) {
                continue;
            }

            if (segmentsIntersect(
                    points.get(i), points.get((i + 1) % points.size()),
                    points.get(j), points.get((j + 1) % points.size()))) {
                return true;
            }
        }

        return false;
    }

    private static boolean segmentsIntersect(JsonNode a, JsonNode b, JsonNode c, JsonNode d) {
        final var abC = orientation(a, b, c);
        final var abD = orientation(a, b, d);
        final var cdA = orientation(c, d, a);
        final var cdB = orientation(c, d, b);

        if (abC == 0 && onSegment(a, b, c)) {
            return true;
        }

        if (abD == 0 && onSegment(a, b, d)) {
            return true;
        }

        if (cdA == 0 && onSegment(c, d, a)) {
            return true;
        }

        if (cdB == 0 && onSegment(c, d, b)) {
            return true;
        }

        return (abC > 0) != (abD > 0) && (cdA > 0) != (cdB > 0);
    }

    private static long orientation(JsonNode a, JsonNode b, JsonNode c) {
        return (long) (b.get(0).asInt() - a.get(0).asInt()) * (c.get(1).asInt() - a.get(1).asInt())
                - (long) (b.get(1).asInt() - a.get(1).asInt()) * (c.get(0).asInt() - a.get(0).asInt());
    }

    private static boolean onSegment(JsonNode a, JsonNode b, JsonNode point) {
        final var x = point.get(0).asInt();
        final var y = point.get(1).asInt();

        return x >= Math.min(a.get(0).asInt(), b.get(0).asInt())
                && x <= Math.max(a.get(0).asInt(), b.get(0).asInt())
                && y >= Math.min(a.get(1).asInt(), b.get(1).asInt())
                && y <= Math.max(a.get(1).asInt(), b.get(1).asInt());
    }

    private static Set<String> collectInputIds(ComponentNode node) {
        final var ids = new HashSet<String>();
        collectInputIds(node, ids);
        return ids;
    }

    private static void collectInputIds(ComponentNode node, Set<String> ids) {
        if (node.type().equals("list")) {
            return;
        }

        final var id = node.props().get("id");
        if (node.type().equals("input") && id != null && id.isTextual()) {
            ids.add(id.asText());
        }

        for (final var child : node.children()) {
            collectInputIds(child, ids);
        }
    }

    @RequiredArgsConstructor
    private static class NodeValidator {
        private final Map<String, TypeSchema> actions;
        private final Map<String, AssetInfo> assets;
        private final Set<String> screenInputs;
        private int nodeCount = 0;

        ValidationResult validateNode(
                ComponentNode node,
                String path,
                Map<String, TypeSchema> scope,
                int depth,
                String parentType,
                Set<String> inputs
        ) {
            nodeCount++;
            if (nodeCount > Limits.MAX_NODES || depth > Limits.MAX_DEPTH) {
                return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, path);
            }

            if (!COMPONENT_PROPS.containsKey(node.type())) {
                return ValidationResult.fail(ValidationErrorCode.UNKNOWN_COMPONENT, path);
            }

            final var keyCheck = validateKey(node.key(), path + ".key", scope);
            if (!keyCheck.isValid()) {
                return keyCheck;
            }

            final var slotParent = SLOT_PARENTS.get(node.type());
            if (slotParent != null && !slotParent.equals(parentType)) {
                return ValidationResult.fail(ValidationErrorCode.MISPLACED_COMPONENT, path);
            }

            final var propsCheck = validateProps(node, path, scope, inputs);
            if (!propsCheck.isValid()) {
                return propsCheck;
            }

            return validateChildren(node, path, scope, depth, inputs);
        }

        private static ValidationResult validateKey(JsonNode key, String path, Map<String, TypeSchema> scope) {
            if (key == null || key.isTextual() || key.isIntegralNumber()) {
                return ValidationResult.ok();
            }

            if (!isBinding(key)) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_KEY, path);
            }

            final var resolved = ResolvedPath.resolve(scope, key.get("$bind").asText());
            if (resolved == null) {
                return ValidationResult.fail(ValidationErrorCode.UNDECLARED_BINDING, path);
            }

            final var schema = TypeSchema.unwrap(resolved.schema());
            if (!(schema instanceof TypeSchema.StringType
                    || schema instanceof TypeSchema.IntType
                    || schema instanceof TypeSchema.LongType)) {
                return ValidationResult.fail(ValidationErrorCode.BINDING_TYPE_MISMATCH, path);
            }

            return ValidationResult.ok();
        }

        private ValidationResult validateProps(
                ComponentNode node,
                String path,
                Map<String, TypeSchema> scope,
                Set<String> inputs
        ) {
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

            final var visualCheck = VisualPropValidator.validate(node, path, scope);
            if (!visualCheck.isValid()) {
                return visualCheck;
            }

            if (node.type().equals("button") && node.props().containsKey("payload")) {
                final var inputsCheck = validateInputReferences(
                        node.props().get("payload"), path + ".props.payload", inputs);
                if (!inputsCheck.isValid()) {
                    return inputsCheck;
                }

                final var payloadCheck = validateButtonPayload(node, path + ".props.payload", scope);
                if (!payloadCheck.isValid()) {
                    return payloadCheck;
                }
            }

            if (node.type().equals("grid")) {
                final var gridCheck = validateGridProps(node, path);
                if (!gridCheck.isValid()) {
                    return gridCheck;
                }
            }

            final var direction = node.props().get("direction");
            if (node.type().equals("scroll") && direction != null
                    && !Set.of("vertical", "horizontal", "both").contains(direction.asText())) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_VALUE, path + ".props.direction");
            }

            final var maxLength = node.props().get("maxLength");
            if (node.type().equals("input") && maxLength != null
                    && (!positiveInteger(maxLength) || maxLength.asInt() > 4096)) {
                return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, path + ".props.maxLength");
            }

            final var inputId = node.props().get("id");
            if (node.type().equals("input")
                    && (inputId == null || !inputId.isTextual() || inputId.asText().isEmpty())) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path + ".props.id");
            }

            final var defaultTab = node.props().get("defaultTab");
            if (node.type().equals("tabs") && (defaultTab == null || !defaultTab.isTextual())) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path + ".props.defaultTab");
            }

            if (node.props().containsKey("shape") && node.props().containsKey("borderRadius")) {
                return ValidationResult.fail(
                        ValidationErrorCode.INVALID_PROP_COMBINATION, path + ".props.borderRadius");
            }

            if (node.props().containsKey("shape") && !validPolygon(node.props().get("shape"))) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_POLYGON, path + ".props.shape");
            }

            for (final var transform : List.of("rotate", "skewX", "skewY")) {
                if (hasTransformValue(node.props().get(transform)) && containsClip(node)) {
                    return ValidationResult.fail(
                            ValidationErrorCode.UNSUPPORTED_COMBINATION, path + ".props." + transform);
                }
            }

            for (final var transform : List.of("rotate", "scale", "skewX", "skewY")) {
                if (hasTransformValue(node.props().get(transform)) && containsInput(node)) {
                    return ValidationResult.fail(
                            ValidationErrorCode.UNSUPPORTED_COMBINATION, path + ".props." + transform);
                }
            }

            return ValidationResult.ok();
        }

        private ValidationResult validateGridProps(ComponentNode node, String path) {
            final var props = node.props();
            for (final var name : List.of("cellWidth", "cellHeight")) {
                if (!props.containsKey(name)) {
                    return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_VALUE, path + ".props." + name);
                }
            }

            final var columns = props.get("columns");
            final var width = props.get("width");
            if (columns == null && (width == null || (!width.isNumber() && !isBinding(width)
                    && !width.asText().equals("fill")))) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_LAYOUT, path + ".props.columns");
            }

            return ValidationResult.ok();
        }

        private ValidationResult validateInputReferences(JsonNode value, String path, Set<String> inputs) {
            if (isInputReference(value)) {
                return inputs.contains(value.get("$input").asText())
                        ? ValidationResult.ok()
                        : ValidationResult.fail(ValidationErrorCode.UNDECLARED_INPUT, path);
            }

            if (value != null && value.isObject()) {
                return validateObjectInputReferences(value, path, inputs);
            }

            if (value != null && value.isArray()) {
                return validateArrayInputReferences(value, path, inputs);
            }

            return ValidationResult.ok();
        }

        private ValidationResult validateObjectInputReferences(JsonNode value, String path, Set<String> inputs) {
            for (final var entry : value.properties()) {
                final var result = validateInputReferences(entry.getValue(), path + "." + entry.getKey(), inputs);
                if (!result.isValid()) {
                    return result;
                }
            }

            return ValidationResult.ok();
        }

        private ValidationResult validateArrayInputReferences(JsonNode value, String path, Set<String> inputs) {
            for (int i = 0; i < value.size(); i++) {
                final var result = validateInputReferences(value.get(i), path + "[" + i + "]", inputs);
                if (!result.isValid()) {
                    return result;
                }
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
                case "input.value", "input.placeholder", "tab.label" -> value.isTextual()
                        ? ValidationResult.ok()
                        : validateBinding(value, path, scope, TypeSchema.StringType.class::isInstance);
                case "input.id", "tab.id", "tabs.defaultTab" -> value.isTextual()
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
                int depth,
                Set<String> inputs
        ) {
            final var children = node.children();
            final var literalKeys = new HashSet<String>();

            for (int i = 0; i < children.size(); i++) {
                final var key = children.get(i).key();
                if (key == null || (!key.isTextual() && !key.isIntegralNumber())) {
                    continue;
                }

                final var identity = key.getNodeType() + ":" + key.asText();
                if (!literalKeys.add(identity)) {
                    return ValidationResult.fail(ValidationErrorCode.DUPLICATE_KEY, path + ".children[" + i + "].key");
                }
            }

            if (node.type().equals("match")) {
                final var slotsCheck = validateMatchSlots(node, children, path, scope);
                if (!slotsCheck.isValid()) {
                    return slotsCheck;
                }
            }

            if (node.type().equals("tabs")) {
                final var tabsCheck = validateTabs(node, children, path);
                if (!tabsCheck.isValid()) {
                    return tabsCheck;
                }
            }

            for (int i = 0; i < children.size(); i++) {
                final var child = children.get(i);
                final var childPath = path + ".children[" + i + "]";

                if (child.type().equals("fallback") && i != children.size() - 1) {
                    return ValidationResult.fail(ValidationErrorCode.MISPLACED_COMPONENT, childPath);
                }

                final var childScope = node.type().equals("list") && i == 0 ? listItemScope(node, scope) : scope;
                final var childInputs = node.type().equals("list") && i == 0
                        ? listInputs(child)
                        : inputs;
                final var result = validateNode(child, childPath, childScope, depth + 1, node.type(), childInputs);
                if (!result.isValid()) {
                    return result;
                }
            }

            return ValidationResult.ok();
        }

        private Set<String> listInputs(ComponentNode template) {
            final var ids = new HashSet<String>(screenInputs);
            if (template.key() != null) {
                ids.addAll(collectInputIds(template));
            }

            return ids;
        }

        private static ValidationResult validateTabs(ComponentNode node, List<ComponentNode> children, String path) {
            final var ids = new HashSet<String>();

            for (int i = 0; i < children.size(); i++) {
                final var child = children.get(i);
                final var childPath = path + ".children[" + i + "]";
                if (!child.type().equals("tab")) {
                    return ValidationResult.fail(ValidationErrorCode.MISPLACED_COMPONENT, childPath);
                }

                final var id = child.props().get("id");
                if (id == null || !id.isTextual() || id.asText().isEmpty()) {
                    return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, childPath + ".props.id");
                }

                if (!child.props().containsKey("label")) {
                    return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, childPath + ".props.label");
                }

                if (!ids.add(id.asText())) {
                    return ValidationResult.fail(ValidationErrorCode.DUPLICATE_TAB, childPath + ".props.id");
                }
            }

            final var defaultTab = node.props().get("defaultTab");
            if (!ids.contains(defaultTab.asText())) {
                return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_VALUE, path + ".props.defaultTab");
            }

            return ValidationResult.ok();
        }

        private static boolean containsInput(ComponentNode node) {
            return node.type().equals("input") || node.children().stream().anyMatch(NodeValidator::containsInput);
        }

        private static boolean containsClip(ComponentNode node) {
            final var overflow = node.props().get("overflow");
            if (node.type().equals("scroll") || overflow != null && overflow.isTextual()
                    && overflow.asText().equals("hidden")) {
                return true;
            }

            return node.children().stream().anyMatch(NodeValidator::containsClip);
        }

        private static boolean hasTransformValue(JsonNode value) {
            return isBinding(value) || nonZero(value);
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
        if (isInputReference(value)) {
            return TypeSchema.unwrap(schema) instanceof TypeSchema.StringType
                    ? ValidationResult.ok()
                    : ValidationResult.fail(ValidationErrorCode.PAYLOAD_SCHEMA_MISMATCH, path);
        }
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
