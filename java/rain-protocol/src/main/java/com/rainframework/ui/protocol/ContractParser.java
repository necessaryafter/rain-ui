package com.rainframework.ui.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainframework.ui.protocol.validation.JsonLimitChecker;
import com.rainframework.ui.protocol.validation.ValidationErrorCode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ContractParser {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final JsonLimitChecker limitChecker = new JsonLimitChecker();

    public Contract parse(String content) throws ParseException {
        final var contentBytes = content.getBytes(StandardCharsets.UTF_8);
        JsonNode root;

        final var limits = limitChecker.checkContract(content);
        if (!limits.isValid()) {
            throw new ParseException(limits.getError().getCode(), limits.getError().getPath());
        }

        try {
            root = mapper.readTree(content);
        } catch (Exception e) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "root");
        }

        if (!root.isObject()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "root");
        }

        // Parse schemaVersion
        final var schemaVersionNode = root.get("schemaVersion");
        if (schemaVersionNode == null || !schemaVersionNode.isInt()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "schemaVersion");
        }

        final var schemaVersion = schemaVersionNode.asInt();
        if (schemaVersion != 0) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "schemaVersion");
        }

        // Parse id
        final var idNode = root.get("id");
        if (idNode == null || !idNode.isTextual()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "id");
        }

        final var id = idNode.asText();
        final var propertiesNode = root.get("properties");
        if (propertiesNode == null || !propertiesNode.isObject()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "properties");
        }

        final var properties = new HashMap<String, TypeSchema>();
        for (final var it = propertiesNode.fields(); it.hasNext(); ) {
            final var entry = it.next();
            properties.put(entry.getKey(), parseTypeSchema(entry.getValue(), "properties." + entry.getKey()));
        }

        // Parse actions
        final var actionsNode = root.get("actions");
        if (actionsNode == null || !actionsNode.isObject()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "actions");
        }

        final var actions = new HashMap<String, TypeSchema>();
        for (final var it = actionsNode.fields(); it.hasNext(); ) {
            final var entry = it.next();
            actions.put(entry.getKey(), parseTypeSchema(entry.getValue(), "actions." + entry.getKey()));
        }

        // Parse root component
        final var rootComponentNode = root.get("root");
        if (rootComponentNode == null) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "root");
        }
        final var rootComponent = parseComponentNode(rootComponentNode, "root");

        final var assets = parseAssets(root.get("assets"));
        final var assetNames = parseAssetNames(root.get("assetNames"));

        return new Contract(
                schemaVersion,
                id,
                properties,
                actions,
                rootComponent,
                assets,
                assetNames,
                contentBytes.length);
    }

    private Map<String, AssetInfo> parseAssets(JsonNode node) throws ParseException {
        if (node == null) {
            return Map.of();
        }

        if (!node.isObject()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "assets");
        }

        final var assets = new HashMap<String, AssetInfo>();
        for (final var it = node.fields(); it.hasNext(); ) {
            final var entry = it.next();
            final var info = entry.getValue();

            assets.put(entry.getKey(), new AssetInfo(
                    info.path("type").isTextual() ? info.get("type").asText() : null,
                    integral(info.path("bytes")),
                    integral(info.path("width")),
                    integral(info.path("height")),
                    integral(info.path("frames"))));
        }

        return assets;
    }

    private static Long integral(JsonNode node) {
        return node.isIntegralNumber() ? node.asLong() : null;
    }

    private Map<String, String> parseAssetNames(JsonNode node) throws ParseException {
        if (node == null) {
            return Map.of();
        }

        if (!node.isObject()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "assetNames");
        }

        final var names = new HashMap<String, String>();
        for (final var it = node.fields(); it.hasNext(); ) {
            final var entry = it.next();

            names.put(entry.getKey(), entry.getValue().isTextual() ? entry.getValue().asText() : null);
        }

        return names;
    }

    private TypeSchema parseTypeSchema(JsonNode node, String path) throws ParseException {
        if (!node.isObject()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
        }

        final var kindNode = node.get("kind");
        if (kindNode == null || !kindNode.isTextual()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
        }

        final var base = parseBaseType(node, kindNode.asText(), path);

        final var defaultNode = node.get("default");
        if (defaultNode != null) {
            return new TypeSchema.OptionalType(base, defaultNode);
        }

        final var optionalNode = node.get("optional");
        if (optionalNode == null) {
            return base;
        }

        if (!optionalNode.isBoolean()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
        }

        return optionalNode.asBoolean() ? new TypeSchema.OptionalType(base, null) : base;
    }

    private TypeSchema parseBaseType(JsonNode node, String kind, String path) throws ParseException {
        return switch (kind) {
            case "string" -> new TypeSchema.StringType();
            case "int" -> new TypeSchema.IntType();
            case "long" -> new TypeSchema.LongType();
            case "double" -> new TypeSchema.DoubleType();
            case "bool" -> new TypeSchema.BoolType();
            case "item" -> new TypeSchema.ItemType();
            case "asset" -> new TypeSchema.AssetType();
            case "list" -> {
                final var ofNode = node.get("of");
                if (ofNode == null) {
                    throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
                }
                final var of = parseTypeSchema(ofNode, path + ".of");
                yield new TypeSchema.ListType(of);
            }
            case "object" -> {
                final var fieldsNode = node.get("fields");
                if (fieldsNode == null || !fieldsNode.isObject()) {
                    throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
                }
                final var fields = new HashMap<String, TypeSchema>();
                for (final var it = fieldsNode.fields(); it.hasNext(); ) {
                    final var entry = it.next();
                    fields.put(entry.getKey(), parseTypeSchema(entry.getValue(), path + ".fields." + entry.getKey()));
                }
                yield new TypeSchema.ObjectType(fields);
            }
            default -> throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
        };
    }

    private ComponentNode parseComponentNode(JsonNode node, String path) throws ParseException {
        if (!node.isObject()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
        }

        final var type = extractRequiredString(node, "type", path);
        final var props = extractRequiredProps(node, path);
        final var children = extractChildren(node, path);

        return new ComponentNode(type, props, children);
    }

    private String extractRequiredString(JsonNode node, String field, String path) throws ParseException {
        final var fieldNode = node.get(field);
        if (fieldNode == null || !fieldNode.isTextual()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
        }
        return fieldNode.asText();
    }

    private Map<String, JsonNode> extractRequiredProps(JsonNode node, String path) throws ParseException {
        final var propsNode = node.get("props");
        if (propsNode == null || !propsNode.isObject()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
        }

        final var props = new HashMap<String, JsonNode>();
        for (final var it = propsNode.fields(); it.hasNext(); ) {
            final var entry = it.next();

            props.put(entry.getKey(), entry.getValue());
        }

        return props;
    }

    private List<ComponentNode> extractChildren(JsonNode node, String path) throws ParseException {
        final var childrenNode = node.get("children");
        if (childrenNode == null || !childrenNode.isArray()) {
            throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
        }

        final var children = new ArrayList<ComponentNode>();
        int index = 0;

        for (final var child : childrenNode) {
            children.add(parseComponentNode(child, path + ".children[" + index + "]"));
            index++;
        }

        return children;
    }
}
