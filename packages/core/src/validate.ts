import { Limits, type ValidationErrorCode } from "./index";
import { itemScopeOf, resolvePath, type ResolvedPath } from "./path";
import type { TypeSchema } from "./types";

export interface ValidationError {
  code: ValidationErrorCode;
  path: string;
}

export type ValidationResultOk = { ok: true };
export type ValidationResultErr = { ok: false; error: ValidationError };
export type ValidationResult = ValidationResultOk | ValidationResultErr;

type Scope = Record<string, TypeSchema>;

const OK: ValidationResultOk = { ok: true };
const ID_PATTERN = /^[a-z0-9_-]+:[a-z0-9_/-]+$/;
const COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/;
const KINDS = new Set(["string", "int", "long", "double", "bool", "item", "list", "object"]);
const DEFAULTABLE_KINDS = new Set(["string", "int", "long", "double", "bool"]);
const MATCHABLE_KINDS = new Set(["string", "int", "long", "bool"]);

const ALLOWED_PROPS: Record<string, Set<string>> = {
  column: new Set(["gap", "padding", "align", "justify", "width", "height"]),
  row: new Set(["gap", "padding", "align", "justify", "width", "height"]),
  text: new Set(["value", "color", "align", "shadow"]),
  item: new Set(["value", "size"]),
  button: new Set(["action", "payload", "disabled"]),
  list: new Set(["source"]),
  show: new Set(["when"]),
  match: new Set(["value"]),
  case: new Set(["is"]),
  default: new Set(),
  fallback: new Set(),
};

// Components that only exist as a slot of a specific parent.
const SLOT_PARENTS: Record<string, string> = {
  case: "match",
  default: "match",
  fallback: "show",
};

function fail(code: ValidationErrorCode, path: string): ValidationResultErr {
  return { ok: false, error: { code, path } };
}

export function validateContract(json: unknown, options?: { sourceBytes?: number }): ValidationResult {
  if (!isObject(json)) return fail("UNKNOWN_SCHEMA_VERSION", "root");

  const contract = json as Record<string, any>;

  const sourceBytes = options?.sourceBytes ?? Buffer.byteLength(JSON.stringify(contract), "utf-8");
  if (sourceBytes > Limits.MAX_CONTRACT_BYTES) return fail("LIMIT_EXCEEDED", "root");
  if (exceedsDepth(contract, Limits.MAX_JSON_DEPTH)) return fail("LIMIT_EXCEEDED", "root");

  if (contract.schemaVersion !== 0) return fail("UNKNOWN_SCHEMA_VERSION", "schemaVersion");
  if (!ID_PATTERN.test(contract.id)) return fail("INVALID_ID", "id");
  if (!isObject(contract.properties)) return fail("UNKNOWN_SCHEMA_VERSION", "properties");
  if (!isObject(contract.actions)) return fail("UNKNOWN_SCHEMA_VERSION", "actions");
  if (Object.keys(contract.actions).length > Limits.MAX_ACTIONS) return fail("LIMIT_EXCEEDED", "actions");

  for (const actionId of Object.keys(contract.actions)) {
    if (!ID_PATTERN.test(actionId)) return fail("INVALID_ID", "actions");
  }

  const propertiesCheck = validateSchemas(contract.properties, "properties", true);
  if (!propertiesCheck.ok) return propertiesCheck;

  const actionsCheck = validateSchemas(contract.actions, "actions", false);
  if (!actionsCheck.ok) return actionsCheck;

  return new NodeValidator(contract.actions).validateNode(contract.root, "root", contract.properties, 0, undefined);
}

function validateSchemas(schemas: Record<string, unknown>, path: string, allowDefault: boolean): ValidationResult {
  for (const [name, schema] of Object.entries(schemas)) {
    const result = validateSchema(schema, `${path}.${name}`, allowDefault);
    if (!result.ok) return result;
  }

  return OK;
}

function validateSchema(schema: any, path: string, allowDefault: boolean): ValidationResult {
  if (!isObject(schema) || !KINDS.has(schema.kind)) return fail("UNKNOWN_SCHEMA_VERSION", path);

  const defaultCheck = validateDefault(schema as TypeSchema, path, allowDefault);
  if (!defaultCheck.ok) return defaultCheck;

  if (schema.kind === "list") return validateSchema(schema.of, `${path}.of`, allowDefault);
  if (schema.kind !== "object") return OK;
  if (!isObject(schema.fields)) return fail("UNKNOWN_SCHEMA_VERSION", path);

  return validateSchemas(schema.fields, `${path}.fields`, allowDefault);
}

// Defaults are only meaningful for properties the server sends; an action payload comes from the client.
function validateDefault(schema: TypeSchema, path: string, allowDefault: boolean): ValidationResult {
  const value = schema.default;
  if (value === undefined) return OK;
  if (!allowDefault || !DEFAULTABLE_KINDS.has(schema.kind)) return fail("INVALID_DEFAULT", path);
  if (!matchesKind(value, schema.kind)) return fail("INVALID_DEFAULT", path);
  if (typeof value === "string" && value.length > Limits.MAX_STRING_LENGTH) return fail("LIMIT_EXCEEDED", path);

  return OK;
}

class NodeValidator {
  private nodeCount = 0;

  constructor(private readonly actions: Scope) {}

  validateNode(node: any, path: string, scope: Scope, depth: number, parentType: string | undefined): ValidationResult {
    this.nodeCount++;
    if (this.nodeCount > Limits.MAX_NODES) return fail("LIMIT_EXCEEDED", path);
    if (depth > Limits.MAX_DEPTH) return fail("LIMIT_EXCEEDED", path);

    if (!isObject(node) || !(node.type in ALLOWED_PROPS)) return fail("UNKNOWN_COMPONENT", path);

    const slotParent = SLOT_PARENTS[node.type];
    if (slotParent !== undefined && slotParent !== parentType) return fail("MISPLACED_COMPONENT", path);

    const propsCheck = this.validateProps(node, path, scope);
    if (!propsCheck.ok) return propsCheck;

    return this.validateChildren(node, path, scope, depth);
  }

  private validateProps(node: any, path: string, scope: Scope): ValidationResult {
    const allowed = ALLOWED_PROPS[node.type];
    const props = node.props ?? {};

    for (const [key, value] of Object.entries(props)) {
      const propPath = `${path}.props.${key}`;
      if (!allowed.has(key)) return fail("UNKNOWN_PROP", propPath);

      const result = this.validateProp(node.type, key, value, propPath, scope);
      if (!result.ok) return result;
    }

    if (node.type === "button" && "payload" in props) {
      return this.validateButtonPayload(props, `${path}.props.payload`, scope);
    }

    return OK;
  }

  private validateProp(type: string, key: string, value: unknown, path: string, scope: Scope): ValidationResult {
    switch (`${type}.${key}`) {
      case "text.value":
        return validateTextValue(value, path, scope);
      case "text.color":
        return validateColor(value, path, scope);
      case "column.gap":
      case "column.padding":
      case "row.gap":
      case "row.padding":
        return typeof value === "number" ? OK : fail("INVALID_PROP_TYPE", path);
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

  private validateAction(value: unknown, path: string): ValidationResult {
    if (typeof value !== "string") return fail("INVALID_PROP_TYPE", path);
    if (!this.actions[value]) return fail("UNDECLARED_ACTION", path);

    return OK;
  }

  private validateChildren(node: any, path: string, scope: Scope, depth: number): ValidationResult {
    const children: any[] = node.children ?? [];

    if (node.type === "match") {
      const slotsCheck = validateMatchSlots(node, children, path, scope);
      if (!slotsCheck.ok) return slotsCheck;
    }

    for (let i = 0; i < children.length; i++) {
      const child = children[i];
      const childPath = `${path}.children[${i}]`;

      if (child?.type === "fallback" && i !== children.length - 1) return fail("MISPLACED_COMPONENT", childPath);

      const childScope = node.type === "list" && i === 0 ? listItemScope(node, scope) ?? scope : scope;
      const result = this.validateNode(child, childPath, childScope, depth + 1, node.type);
      if (!result.ok) return result;
    }

    return OK;
  }

  private validateButtonPayload(props: Record<string, unknown>, path: string, scope: Scope): ValidationResult {
    const action = props.action;
    if (typeof action !== "string" || !this.actions[action]) return fail("PAYLOAD_SCHEMA_MISMATCH", path);

    return validatePayloadValue(props.payload, this.actions[action], path, scope);
  }
}

// Checks what only the match itself knows: children are cases or one trailing default, and each case literal has the
// type of the matched value and appears once.
function validateMatchSlots(node: any, children: any[], path: string, scope: Scope): ValidationResult {
  const matched = isBinding(node.props?.value) ? resolvePath(scope, node.props.value.$bind) : undefined;
  const seen = new Set<unknown>();

  for (let i = 0; i < children.length; i++) {
    const child = children[i];
    const childPath = `${path}.children[${i}]`;

    if (child?.type === "default") {
      if (i !== children.length - 1) return fail("MISPLACED_COMPONENT", childPath);
      continue;
    }

    if (child?.type !== "case") return fail("MISPLACED_COMPONENT", childPath);

    const literal = child.props?.is;
    const literalPath = `${childPath}.props.is`;
    if (matched && !matchesKind(literal, matched.schema.kind)) return fail("INVALID_PROP_TYPE", literalPath);
    if (seen.has(literal)) return fail("DUPLICATE_CASE", literalPath);

    seen.add(literal);
  }

  return OK;
}

function validateTextValue(value: unknown, path: string, scope: Scope): ValidationResult {
  if (typeof value === "string") {
    return value.length > Limits.MAX_STRING_LENGTH ? fail("LIMIT_EXCEEDED", path) : OK;
  }

  return validateBinding(value, path, scope, (kind) => kind === "string" || kind === "int" || kind === "long");
}

// A bad color that arrives at runtime through a binding falls back to the default color on the client.
function validateColor(value: unknown, path: string, scope: Scope): ValidationResult {
  if (typeof value === "string") {
    return COLOR_PATTERN.test(value) ? OK : fail("INVALID_PROP_TYPE", path);
  }

  return validateBinding(value, path, scope, (kind) => kind === "string");
}

function validateBinding(
  value: unknown,
  path: string,
  scope: Scope,
  acceptsKind: (kind: TypeSchema["kind"]) => boolean,
): ValidationResult {
  if (!isBinding(value)) return fail("INVALID_PROP_TYPE", path);

  const resolved = resolvePath(scope, value.$bind);
  if (!resolved) return fail("UNDECLARED_BINDING", path);
  if (!acceptsKind(resolved.schema.kind)) return fail("BINDING_TYPE_MISMATCH", path);

  return OK;
}

function listItemScope(node: any, scope: Scope): Scope | undefined {
  const source = node.props?.source;
  if (!isBinding(source)) return undefined;

  return itemScopeOf(scope, source.$bind);
}

function validatePayloadValue(value: unknown, schema: TypeSchema, path: string, scope: Scope): ValidationResult {
  if (isBinding(value)) {
    const resolved = resolvePath(scope, value.$bind);
    if (!resolved) return fail("UNDECLARED_BINDING", path);
    if (!isAssignable(resolved, schema)) return fail("BINDING_TYPE_MISMATCH", path);

    return OK;
  }

  if (value === null || value === undefined) {
    return isOptional(schema) ? OK : fail("PAYLOAD_SCHEMA_MISMATCH", path);
  }

  switch (schema.kind) {
    case "object":
      return validatePayloadObject(value, schema.fields, path, scope);
    case "list":
      return validatePayloadList(value, schema.of, path, scope);
    case "item":
      return fail("PAYLOAD_SCHEMA_MISMATCH", path);
    default:
      return matchesKind(value, schema.kind) ? OK : fail("PAYLOAD_SCHEMA_MISMATCH", path);
  }
}

function validatePayloadObject(value: unknown, fields: Scope, path: string, scope: Scope): ValidationResult {
  if (!isObject(value)) return fail("PAYLOAD_SCHEMA_MISMATCH", path);

  for (const [key, field] of Object.entries(fields)) {
    if (!(key in value) && !isOptional(field)) return fail("PAYLOAD_SCHEMA_MISMATCH", `${path}.${key}`);
  }

  for (const [key, field] of Object.entries(value)) {
    const fieldPath = `${path}.${key}`;
    if (!(key in fields)) return fail("PAYLOAD_SCHEMA_MISMATCH", fieldPath);

    const result = validatePayloadValue(field, fields[key], fieldPath, scope);
    if (!result.ok) return result;
  }

  return OK;
}

function validatePayloadList(value: unknown, of: TypeSchema, path: string, scope: Scope): ValidationResult {
  if (!Array.isArray(value)) return fail("PAYLOAD_SCHEMA_MISMATCH", path);

  for (let i = 0; i < value.length; i++) {
    const result = validatePayloadValue(value[i], of, `${path}[${i}]`, scope);
    if (!result.ok) return result;
  }

  return OK;
}

// A bound value fits a payload field when the shapes match and, if the field is required, the value is always present.
function isAssignable(bound: ResolvedPath, target: TypeSchema): boolean {
  if (!sameShape(bound.schema, target)) return false;

  return !bound.optional || isOptional(target);
}

function sameShape(a: TypeSchema, b: TypeSchema): boolean {
  if (a.kind !== b.kind) return false;
  if (a.kind === "list" && b.kind === "list") return sameShape(a.of, b.of);
  if (a.kind !== "object" || b.kind !== "object") return true;

  const aKeys = Object.keys(a.fields).sort();
  const bKeys = Object.keys(b.fields).sort();
  if (aKeys.join("\0") !== bKeys.join("\0")) return false;

  return aKeys.every((key) => sameShape(a.fields[key], b.fields[key]));
}

function isOptional(schema: TypeSchema): boolean {
  return schema.optional === true || schema.default !== undefined;
}

function matchesKind(value: unknown, kind: TypeSchema["kind"]): boolean {
  switch (kind) {
    case "string":
      return typeof value === "string";
    case "int":
    case "long":
      return Number.isInteger(value);
    case "double":
      return typeof value === "number";
    case "bool":
      return typeof value === "boolean";
    default:
      return false;
  }
}

// Stops descending as soon as the limit is passed, so recursion never goes deeper than maxDepth + 1.
function exceedsDepth(value: unknown, maxDepth: number): boolean {
  if (value === null || typeof value !== "object") return false;
  if (maxDepth === 0) return true;

  const children = Array.isArray(value) ? value : Object.values(value);
  return children.some((child) => exceedsDepth(child, maxDepth - 1));
}

function isBinding(value: unknown): value is { $bind: string } {
  return isObject(value) && typeof value.$bind === "string" && Object.keys(value).length === 1;
}

function isObject(value: unknown): value is Record<string, any> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
