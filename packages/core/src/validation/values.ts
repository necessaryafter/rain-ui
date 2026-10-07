import { Limits } from "./limits";
import { resolvePath, type ResolvedPath } from "../contract/path";
import type { TypeSchema } from "../contract/types";
import {
  OK, fail, isBinding, isInputRef, isObject, isOptional, matchesKind, type Scope, type ValidationResult,
} from "./shared";

const COLOR_PATTERN = /^#(?:[0-9A-Fa-f]{3}|[0-9A-Fa-f]{6})$/;

export function validateTextValue(value: unknown, path: string, scope: Scope): ValidationResult {
  if (typeof value === "string") {
    return value.length > Limits.MAX_STRING_LENGTH ? fail("LIMIT_EXCEEDED", path) : OK;
  }

  return validateBinding(value, path, scope, (kind) => kind === "string" || kind === "int" || kind === "long");
}

// A bad color that arrives at runtime through a binding falls back to the default color on the client.
export function validateColor(value: unknown, path: string, scope: Scope): ValidationResult {
  if (typeof value === "string") {
    return COLOR_PATTERN.test(value) ? OK : fail("INVALID_PROP_TYPE", path);
  }

  return validateBinding(value, path, scope, (kind) => kind === "string");
}

export function validateBinding(
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

export function validatePayloadValue(
  value: unknown,
  schema: TypeSchema,
  path: string,
  scope: Scope,
  inputs: Set<string> = new Set(),
): ValidationResult {
  if (isInputRef(value)) {
    if (!inputs.has(value.$input)) return fail("UNDECLARED_INPUT", path);
    return schema.kind === "string" ? OK : fail("PAYLOAD_SCHEMA_MISMATCH", path);
  }
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
      return validatePayloadObject(value, schema.fields, path, scope, inputs);
    case "list":
      return validatePayloadList(value, schema.of, path, scope, inputs);
    case "item":
      return fail("PAYLOAD_SCHEMA_MISMATCH", path);
    default:
      return matchesKind(value, schema.kind) ? OK : fail("PAYLOAD_SCHEMA_MISMATCH", path);
  }
}

function validatePayloadObject(
  value: unknown,
  fields: Scope,
  path: string,
  scope: Scope,
  inputs: Set<string>,
): ValidationResult {
  if (!isObject(value)) return fail("PAYLOAD_SCHEMA_MISMATCH", path);

  for (const [key, field] of Object.entries(fields)) {
    if (!(key in value) && !isOptional(field)) return fail("PAYLOAD_SCHEMA_MISMATCH", `${path}.${key}`);
  }

  for (const [key, field] of Object.entries(value)) {
    const fieldPath = `${path}.${key}`;
    if (!(key in fields)) return fail("PAYLOAD_SCHEMA_MISMATCH", fieldPath);

    const result = validatePayloadValue(field, fields[key], fieldPath, scope, inputs);
    if (!result.ok) return result;
  }

  return OK;
}

function validatePayloadList(
  value: unknown,
  of: TypeSchema,
  path: string,
  scope: Scope,
  inputs: Set<string>,
): ValidationResult {
  if (!Array.isArray(value)) return fail("PAYLOAD_SCHEMA_MISMATCH", path);

  for (let i = 0; i < value.length; i++) {
    const result = validatePayloadValue(value[i], of, `${path}[${i}]`, scope, inputs);
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
