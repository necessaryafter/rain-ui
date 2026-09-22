package com.rainframework.ui.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ContractParser {
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final int MAX_JSON_DEPTH = Limits.MAX_JSON_DEPTH;

  public Contract parse(String content) throws ParseException {
    byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
    JsonNode root;
    try {
      root = mapper.readTree(content);
    } catch (Exception e) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "root");
    }

    if (!root.isObject()) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "root");
    }

    // Parse schemaVersion
    JsonNode schemaVersionNode = root.get("schemaVersion");
    if (schemaVersionNode == null || !schemaVersionNode.isInt()) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "schemaVersion");
    }
    int schemaVersion = schemaVersionNode.asInt();
    if (schemaVersion != 0) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "schemaVersion");
    }

    // Parse id
    JsonNode idNode = root.get("id");
    if (idNode == null || !idNode.isTextual()) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "id");
    }
    String id = idNode.asText();

    // Parse properties
    JsonNode propertiesNode = root.get("properties");
    if (propertiesNode == null || !propertiesNode.isObject()) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "properties");
    }
    Map<String, TypeSchema> properties = new HashMap<>();
    for (var it = propertiesNode.fields(); it.hasNext(); ) {
      var entry = it.next();
      properties.put(entry.getKey(), parseTypeSchema(entry.getValue(), "properties." + entry.getKey()));
    }

    // Parse actions
    JsonNode actionsNode = root.get("actions");
    if (actionsNode == null || !actionsNode.isObject()) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "actions");
    }
    Map<String, TypeSchema> actions = new HashMap<>();
    for (var it = actionsNode.fields(); it.hasNext(); ) {
      var entry = it.next();
      actions.put(entry.getKey(), parseTypeSchema(entry.getValue(), "actions." + entry.getKey()));
    }

    // Parse root component
    JsonNode rootComponentNode = root.get("root");
    if (rootComponentNode == null) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "root");
    }
    ComponentNode rootComponent = parseComponentNode(rootComponentNode, "root", 0);

    return new Contract(schemaVersion, id, properties, actions, rootComponent, contentBytes.length);
  }

  private TypeSchema parseTypeSchema(JsonNode node, String path) throws ParseException {
    if (!node.isObject()) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
    }

    JsonNode kindNode = node.get("kind");
    if (kindNode == null || !kindNode.isTextual()) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
    }

    String kind = kindNode.asText();
    return switch (kind) {
      case "string" -> new TypeSchema.StringType();
      case "int" -> new TypeSchema.IntType();
      case "long" -> new TypeSchema.LongType();
      case "bool" -> new TypeSchema.BoolType();
      case "item" -> new TypeSchema.ItemType();
      case "list" -> {
        JsonNode ofNode = node.get("of");
        if (ofNode == null) {
          throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
        }
        TypeSchema of = parseTypeSchema(ofNode, path + ".of");
        yield new TypeSchema.ListType(of);
      }
      case "object" -> {
        JsonNode fieldsNode = node.get("fields");
        if (fieldsNode == null || !fieldsNode.isObject()) {
          throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
        }
        Map<String, TypeSchema> fields = new HashMap<>();
        for (var it = fieldsNode.fields(); it.hasNext(); ) {
          var entry = it.next();
          fields.put(entry.getKey(), parseTypeSchema(entry.getValue(), path + ".fields." + entry.getKey()));
        }
        yield new TypeSchema.ObjectType(fields);
      }
      default -> throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
    };
  }

  private ComponentNode parseComponentNode(JsonNode node, String path, int depth) throws ParseException {
    if (depth > MAX_JSON_DEPTH) {
      throw new ParseException(ValidationErrorCode.LIMIT_EXCEEDED, path);
    }

    if (!node.isObject()) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
    }

    String type = extractRequiredString(node, "type", path);
    Map<String, JsonNode> props = extractRequiredProps(node, path);
    List<ComponentNode> children = extractChildren(node, path, depth);

    return new ComponentNode(type, props, children);
  }

  private String extractRequiredString(JsonNode node, String field, String path) throws ParseException {
    JsonNode fieldNode = node.get(field);
    if (fieldNode == null || !fieldNode.isTextual()) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
    }
    return fieldNode.asText();
  }

  private Map<String, JsonNode> extractRequiredProps(JsonNode node, String path) throws ParseException {
    JsonNode propsNode = node.get("props");
    if (propsNode == null || !propsNode.isObject()) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
    }

    Map<String, JsonNode> props = new HashMap<>();
    for (var it = propsNode.fields(); it.hasNext(); ) {
      var entry = it.next();
      props.put(entry.getKey(), entry.getValue());
    }
    return props;
  }

  private List<ComponentNode> extractChildren(JsonNode node, String path, int depth) throws ParseException {
    JsonNode childrenNode = node.get("children");
    if (childrenNode == null || !childrenNode.isArray()) {
      throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
    }

    List<ComponentNode> children = new ArrayList<>();
    int index = 0;
    for (JsonNode child : childrenNode) {
      children.add(parseComponentNode(child, path + ".children[" + index + "]", depth + 1));
      index++;
    }
    return children;
  }
}
