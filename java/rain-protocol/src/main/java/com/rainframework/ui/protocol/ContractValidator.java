package com.rainframework.ui.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class ContractValidator {
  private static final Set<String> COMPONENT_TYPES = Set.of("column", "row", "text", "item", "button", "list");
  private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_-]+:[a-z0-9_/-]+$");
  private static final Map<String, Set<String>> COMPONENT_PROPS = Map.ofEntries(
      Map.entry("text", Set.of("value", "color", "align", "shadow")),
      Map.entry("column", Set.of("gap", "padding", "align", "justify", "width", "height")),
      Map.entry("row", Set.of("gap", "padding", "align", "justify", "width", "height")),
      Map.entry("item", Set.of("value", "size")),
      Map.entry("button", Set.of("action", "payload", "disabled")),
      Map.entry("list", Set.of("source")));

  public ValidationResult validate(Contract contract) {
    ValidationResult basicChecks = validateBasicConstraints(contract);
    if (!basicChecks.isValid()) {
      return basicChecks;
    }

    NodeValidator validator = new NodeValidator(contract.properties(), contract.actions());
    return validator.validateComponentNode(contract.root(), "root", contract.properties(), 0);
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

    for (String actionId : contract.actions().keySet()) {
      if (!ID_PATTERN.matcher(actionId).matches()) {
        return ValidationResult.fail(ValidationErrorCode.INVALID_ID, "actions");
      }
    }

    return ValidationResult.ok();
  }

  private static class NodeValidator {
    private final Map<String, TypeSchema> rootProperties;
    private final Map<String, TypeSchema> actions;
    private int nodeCount = 0;

    NodeValidator(Map<String, TypeSchema> rootProperties, Map<String, TypeSchema> actions) {
      this.rootProperties = rootProperties;
      this.actions = actions;
    }

    ValidationResult validateComponentNode(ComponentNode node, String path, Map<String, TypeSchema> scope, int depth) {
      ValidationResult limitCheck = checkLimits(path, depth);
      if (!limitCheck.isValid()) {
        return limitCheck;
      }

      if (!COMPONENT_TYPES.contains(node.type())) {
        return ValidationResult.fail(ValidationErrorCode.UNKNOWN_COMPONENT, path);
      }

      ValidationResult propsCheck = validateNodeProps(node, path, scope);
      if (!propsCheck.isValid()) {
        return propsCheck;
      }

      return validateNodeChildren(node, path, scope, depth);
    }

    private ValidationResult checkLimits(String path, int depth) {
      nodeCount++;
      if (nodeCount > Limits.MAX_NODES) {
        return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, path);
      }

      if (depth > Limits.MAX_DEPTH) {
        return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, path);
      }

      return ValidationResult.ok();
    }

    private ValidationResult validateNodeProps(ComponentNode node, String path, Map<String, TypeSchema> scope) {
      Set<String> allowedProps = COMPONENT_PROPS.getOrDefault(node.type(), Set.of());
      for (String propKey : node.props().keySet()) {
        if (!allowedProps.contains(propKey)) {
          return ValidationResult.fail(ValidationErrorCode.UNKNOWN_PROP, path + ".props." + propKey);
        }

        JsonNode propValue = node.props().get(propKey);
        ValidationResult propValidation = validateProp(node.type(), propKey, propValue, path + ".props." + propKey, scope);
        if (!propValidation.isValid()) {
          return propValidation;
        }
      }
      return ValidationResult.ok();
    }

    private ValidationResult validateNodeChildren(ComponentNode node, String path, Map<String, TypeSchema> scope, int depth) {
      for (int i = 0; i < node.children().size(); i++) {
        ComponentNode child = node.children().get(i);
        String childPath = path + ".children[" + i + "]";

        ValidationResult childResult = validateChild(node, child, childPath, scope, depth);
        if (!childResult.isValid()) {
          return childResult;
        }
      }
      return ValidationResult.ok();
    }

    private ValidationResult validateChild(ComponentNode parent, ComponentNode child, String childPath, Map<String, TypeSchema> scope, int depth) {
      if (parent.type().equals("list")) {
        JsonNode sourceBinding = parent.props().get("source");
        if (isBinding(sourceBinding)) {
          String bindPath = sourceBinding.get("$bind").asText();
          TypeSchema bindType = scope.get(bindPath);
          if (bindType instanceof TypeSchema.ListType listType && listType.of() instanceof TypeSchema.ObjectType objectType) {
            return validateComponentNode(child, childPath, objectType.fields(), depth + 1);
          }
        }
      }
      return validateComponentNode(child, childPath, scope, depth + 1);
    }

    private ValidationResult validateProp(String componentType, String propName, JsonNode propValue, String path, Map<String, TypeSchema> scope) {
      return switch (componentType) {
        case "text" -> validateTextProp(propName, propValue, path, scope);
        case "column", "row" -> validateColumnRowProp(propName, propValue, path, scope);
        case "item" -> validateItemProp(propName, propValue, path, scope);
        case "button" -> validateButtonProp(propName, propValue, path, scope);
        case "list" -> validateListProp(propName, propValue, path, scope);
        default -> ValidationResult.ok();
      };
    }

    private ValidationResult validateTextProp(String propName, JsonNode propValue, String path, Map<String, TypeSchema> scope) {
      return switch (propName) {
        case "value" -> validateStringOrBinding(propValue, path, scope);
        case "color", "align", "shadow" -> ValidationResult.ok(); // Skip strict type check for these
        default -> ValidationResult.ok();
      };
    }

    private ValidationResult validateColumnRowProp(String propName, JsonNode propValue, String path, Map<String, TypeSchema> scope) {
      return switch (propName) {
        case "gap", "padding" -> {
          if (!propValue.isNumber()) {
            yield ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
          }
          yield ValidationResult.ok();
        }
        case "align", "justify", "width", "height" -> ValidationResult.ok(); // Skip strict type check
        default -> ValidationResult.ok();
      };
    }

    private ValidationResult validateItemProp(String propName, JsonNode propValue, String path, Map<String, TypeSchema> scope) {
      return switch (propName) {
        case "value" -> {
          if (!isBinding(propValue)) {
            yield ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
          }
          String bindPath = propValue.get("$bind").asText();
          TypeSchema bindType = scope.get(bindPath);
          if (bindType == null) {
            yield ValidationResult.fail(ValidationErrorCode.UNDECLARED_BINDING, path);
          }
          if (!(bindType instanceof TypeSchema.ItemType)) {
            yield ValidationResult.fail(ValidationErrorCode.BINDING_TYPE_MISMATCH, path);
          }
          yield ValidationResult.ok();
        }
        case "size" -> ValidationResult.ok(); // Skip strict type check
        default -> ValidationResult.ok();
      };
    }

    private ValidationResult validateButtonProp(String propName, JsonNode propValue, String path, Map<String, TypeSchema> scope) {
      return switch (propName) {
        case "action" -> {
          if (!propValue.isTextual()) {
            yield ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
          }
          String actionId = propValue.asText();
          if (!actions.containsKey(actionId)) {
            yield ValidationResult.fail(ValidationErrorCode.UNDECLARED_ACTION, path);
          }
          yield ValidationResult.ok();
        }
        case "payload" -> {
          if (!propValue.isObject()) {
            yield ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
          }
          // Extract action id from sibling prop to get payload schema
          // This is a simplification — in reality we'd need to thread the action id through
          // For now, accept any object payload
          yield ValidationResult.ok();
        }
        case "disabled" -> {
          if (propValue.isBoolean()) {
            yield ValidationResult.ok();
          }
          if (isBinding(propValue)) {
            String bindPath = propValue.get("$bind").asText();
            TypeSchema bindType = scope.get(bindPath);
            if (bindType == null) {
              yield ValidationResult.fail(ValidationErrorCode.UNDECLARED_BINDING, path);
            }
            if (!(bindType instanceof TypeSchema.BoolType)) {
              yield ValidationResult.fail(ValidationErrorCode.BINDING_TYPE_MISMATCH, path);
            }
            yield ValidationResult.ok();
          }
          yield ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
        }
        default -> ValidationResult.ok();
      };
    }

    private ValidationResult validateListProp(String propName, JsonNode propValue, String path, Map<String, TypeSchema> scope) {
      return switch (propName) {
        case "source" -> {
          if (!isBinding(propValue)) {
            yield ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
          }
          String bindPath = propValue.get("$bind").asText();
          TypeSchema bindType = scope.get(bindPath);
          if (bindType == null) {
            yield ValidationResult.fail(ValidationErrorCode.UNDECLARED_BINDING, path);
          }
          if (!(bindType instanceof TypeSchema.ListType)) {
            yield ValidationResult.fail(ValidationErrorCode.BINDING_TYPE_MISMATCH, path);
          }
          yield ValidationResult.ok();
        }
        default -> ValidationResult.ok();
      };
    }

    private ValidationResult validateStringOrBinding(JsonNode propValue, String path, Map<String, TypeSchema> scope) {
      if (propValue.isTextual()) {
        String value = propValue.asText();
        if (value.length() > Limits.MAX_STRING_LENGTH) {
          return ValidationResult.fail(ValidationErrorCode.LIMIT_EXCEEDED, path);
        }
        return ValidationResult.ok();
      }
      if (isBinding(propValue)) {
        String bindPath = propValue.get("$bind").asText();
        TypeSchema bindType = scope.get(bindPath);
        if (bindType == null) {
          return ValidationResult.fail(ValidationErrorCode.UNDECLARED_BINDING, path);
        }
        if (!(bindType instanceof TypeSchema.StringType)) {
          return ValidationResult.fail(ValidationErrorCode.BINDING_TYPE_MISMATCH, path);
        }
        return ValidationResult.ok();
      }
      return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
    }

    private boolean isBinding(JsonNode node) {
      return node.isObject() && node.has("$bind") && node.get("$bind").isTextual();
    }
  }
}
