import { Limits } from "./limits";
import {
  ALLOWED_PROPS, MATCHABLE_KINDS, SLOT_PARENTS, collectInputs, containsClip, containsInput, listItemScope,
  validateGridProps, validateInputProps, validateKey, validateMatchSlots, validateTabs,
} from "./node-rules";
import { hasNonZero, validateLayoutProps } from "./layout";
import {
  FONT_TYPES, IMAGE_TYPES, OK, fail, isAssetRef, isBinding, isObject,
  type Assets, type Scope, type ValidationResult,
} from "./shared";
import { validateBinding, validateColor, validatePayloadValue, validateTextValue } from "./values";

export class NodeValidator {
  private nodeCount = 0;

  constructor(
    private readonly actions: Scope,
    private readonly assets: Assets,
    private readonly screenInputs: Set<string>,
  ) {}

  validateNode(
    node: any,
    path: string,
    scope: Scope,
    depth: number,
    parentType: string | undefined,
    inputs: Set<string>,
  ): ValidationResult {
    this.nodeCount++;
    if (this.nodeCount > Limits.MAX_NODES) return fail("LIMIT_EXCEEDED", path);
    if (depth > Limits.MAX_DEPTH) return fail("LIMIT_EXCEEDED", path);

    if (!isObject(node) || !(node.type in ALLOWED_PROPS)) return fail("UNKNOWN_COMPONENT", path);

    const keyCheck = validateKey(node.key, `${path}.key`, scope);
    if (!keyCheck.ok) return keyCheck;

    const slotParent = SLOT_PARENTS[node.type];
    if (slotParent !== undefined && slotParent !== parentType) return fail("MISPLACED_COMPONENT", path);

    const propsCheck = this.validateProps(node, path, scope, inputs);
    if (!propsCheck.ok) return propsCheck;

    return this.validateChildren(node, path, scope, depth, inputs);
  }

  private validateProps(node: any, path: string, scope: Scope, inputs: Set<string>): ValidationResult {
    const allowed = ALLOWED_PROPS[node.type];
    const props = node.props ?? {};

    for (const [key, value] of Object.entries(props)) {
      const propPath = `${path}.props.${key}`;
      if (!allowed.has(key)) return fail("UNKNOWN_PROP", propPath);

      const result = this.validateProp(node.type, key, value, propPath, scope);
      if (!result.ok) return result;
    }

    if (node.type === "button" && "payload" in props) {
      const payloadCheck = this.validateButtonPayload(props, `${path}.props.payload`, scope, inputs);
      if (!payloadCheck.ok) return payloadCheck;
    }

    if (node.type === "grid") {
      const gridCheck = validateGridProps(node, path, scope);
      if (!gridCheck.ok) return gridCheck;
    }
    if (node.type === "tabs" && typeof props.defaultTab !== "string") {
      return fail("INVALID_PROP_TYPE", `${path}.props.defaultTab`);
    }
    if (node.type === "input") {
      const inputCheck = validateInputProps(props, path);
      if (!inputCheck.ok) return inputCheck;
    }
    const layout = validateLayoutProps(node, path, scope);
    if (!layout.ok) return layout;
    if (containsClip(node) && (hasTransformValue(props.rotate)
      || hasTransformValue(props.skewX) || hasTransformValue(props.skewY))) {
      const transform = hasTransformValue(props.rotate) ? "rotate"
        : hasTransformValue(props.skewX) ? "skewX" : "skewY";
      return fail("UNSUPPORTED_COMBINATION", `${path}.props.${transform}`);
    }
    if (containsInput(node) && (hasTransformValue(props.rotate) || hasTransformValue(props.scale)
      || hasTransformValue(props.skewX) || hasTransformValue(props.skewY))) {
      const transform = hasTransformValue(props.rotate) ? "rotate"
        : hasTransformValue(props.scale) ? "scale" : hasTransformValue(props.skewX) ? "skewX" : "skewY";
      return fail("UNSUPPORTED_COMBINATION", `${path}.props.${transform}`);
    }

    return OK;
  }

  private validateProp(type: string, key: string, value: unknown, path: string, scope: Scope): ValidationResult {
    switch (`${type}.${key}`) {
      case "text.value":
        return validateTextValue(value, path, scope);
      case "text.color":
        return validateColor(value, path, scope);
      case "text.font":
        return this.validateAssetRef(value, path, FONT_TYPES);
      case "image.src":
        return isBinding(value)
          ? validateBinding(value, path, scope, (kind) => kind === "asset")
          : this.validateAssetRef(value, path, IMAGE_TYPES);
      case "scroll.direction":
        return value === "vertical" || value === "horizontal" || value === "both"
          ? OK : fail("INVALID_PROP_VALUE", path);
      case "input.value":
      case "input.placeholder":
      case "tab.label":
        return typeof value === "string" ? OK : validateBinding(value, path, scope, (kind) => kind === "string");
      case "input.id":
      case "tab.id":
      case "tabs.defaultTab":
        return typeof value === "string" ? OK : fail("INVALID_PROP_TYPE", path);
      case "item.value":
        return validateBinding(value, path, scope, (kind) => kind === "item");
      case "button.action":
        return this.validateAction(value, path);
      case "button.disabled":
        return typeof value === "boolean" ? OK : validateBinding(value, path, scope, (kind) => kind === "bool");
      case "list.source":
        return validateBinding(value, path, scope, (kind) => kind === "list");
      case "show.when":
        return validateBinding(value, path, scope, () => true);
      case "match.value":
        return validateBinding(value, path, scope, (kind) => MATCHABLE_KINDS.has(kind));
      default:
        return OK;
    }
  }

  private validateAssetRef(value: unknown, path: string, acceptedTypes: Set<string>): ValidationResult {
    if (!isAssetRef(value)) return fail("INVALID_PROP_TYPE", path);

    const info = this.assets[value.$asset];
    if (!info) return fail("UNDECLARED_ASSET", path);
    if (!acceptedTypes.has(info.type)) return fail("ASSET_TYPE_MISMATCH", path);

    return OK;
  }

  private validateAction(value: unknown, path: string): ValidationResult {
    if (typeof value !== "string") return fail("INVALID_PROP_TYPE", path);
    if (!this.actions[value]) return fail("UNDECLARED_ACTION", path);

    return OK;
  }

  private validateChildren(
    node: any,
    path: string,
    scope: Scope,
    depth: number,
    inputs: Set<string>,
  ): ValidationResult {
    const children: any[] = node.children ?? [];

    const literalKeys = new Set<string>();
    for (let i = 0; i < children.length; i++) {
      const key = children[i]?.key;
      if (typeof key !== "string" && !Number.isInteger(key)) continue;
      const identity = `${typeof key}:${key}`;
      if (literalKeys.has(identity)) return fail("DUPLICATE_KEY", `${path}.children[${i}].key`);
      literalKeys.add(identity);
    }

    if (node.type === "match") {
      const slotsCheck = validateMatchSlots(node, children, path, scope);
      if (!slotsCheck.ok) return slotsCheck;
    }
    if (node.type === "tabs") {
      const tabsCheck = validateTabs(children, node.props?.defaultTab, path);
      if (!tabsCheck.ok) return tabsCheck;
    }

    for (let i = 0; i < children.length; i++) {
      const child = children[i];
      const childPath = `${path}.children[${i}]`;

      if (child?.type === "fallback" && i !== children.length - 1) return fail("MISPLACED_COMPONENT", childPath);

      const childScope = node.type === "list" && i === 0 ? listItemScope(node, scope) ?? scope : scope;
      const childInputs = node.type === "list" && i === 0
        ? new Set([...this.screenInputs, ...(child?.key === undefined ? [] : collectInputs(child))])
        : inputs;
      const result = this.validateNode(child, childPath, childScope, depth + 1, node.type, childInputs);
      if (!result.ok) return result;
    }

    return OK;
  }

  private validateButtonPayload(
    props: Record<string, unknown>,
    path: string,
    scope: Scope,
    inputs: Set<string>,
  ): ValidationResult {
    const action = props.action;
    if (typeof action !== "string" || !this.actions[action]) return fail("PAYLOAD_SCHEMA_MISMATCH", path);

    return validatePayloadValue(props.payload, this.actions[action], path, scope, inputs);
  }
}

function hasTransformValue(value: unknown): boolean {
  return isBinding(value) || hasNonZero(value);
}
