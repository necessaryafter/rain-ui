import type { AssetInfo, TypeSchema } from "../contract/types";

export type ValidationErrorCode =
  | "UNKNOWN_SCHEMA_VERSION"
  | "UNKNOWN_COMPONENT"
  | "UNKNOWN_PROP"
  | "INVALID_PROP_TYPE"
  | "UNDECLARED_BINDING"
  | "BINDING_TYPE_MISMATCH"
  | "UNDECLARED_ACTION"
  | "PAYLOAD_SCHEMA_MISMATCH"
  | "DUPLICATE_SCREEN_ID"
  | "INVALID_ID"
  | "LIMIT_EXCEEDED"
  | "INVALID_DEFAULT"
  | "MISPLACED_COMPONENT"
  | "DUPLICATE_CASE"
  | "UNDECLARED_ASSET"
  | "ASSET_TYPE_MISMATCH"
  | "UNSUPPORTED_ASSET"
  | "INVALID_ASSET_HASH"
  | "INVALID_KEY"
  | "DUPLICATE_KEY"
  | "INVALID_PROP_VALUE"
  | "INVALID_LAYOUT"
  | "UNDECLARED_INPUT"
  | "DUPLICATE_TAB"
  | "INVALID_POLYGON"
  | "INVALID_PROP_COMBINATION"
  | "UNSUPPORTED_COMBINATION";

export interface ValidationError {
  code: ValidationErrorCode;
  path: string;
}

export type ValidationResultOk = { ok: true };
export type ValidationResultErr = { ok: false; error: ValidationError };
export type ValidationResult = ValidationResultOk | ValidationResultErr;

export type Scope = Record<string, TypeSchema>;
export type Assets = Record<string, AssetInfo>;

export const OK: ValidationResultOk = { ok: true };
export const IMAGE_TYPES = new Set(["image/png", "image/jpeg", "image/gif"]);
export const FONT_TYPES = new Set(["font/ttf", "font/otf"]);

export function fail(code: ValidationErrorCode, path: string): ValidationResultErr {
  return { ok: false, error: { code, path } };
}

export function isOptional(schema: TypeSchema): boolean {
  return schema.optional === true || schema.default !== undefined;
}

export function matchesKind(value: unknown, kind: TypeSchema["kind"]): boolean {
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
export function exceedsDepth(value: unknown, maxDepth: number): boolean {
  if (value === null || typeof value !== "object") return false;
  if (maxDepth === 0) return true;

  const children = Array.isArray(value) ? value : Object.values(value);
  return children.some((child) => exceedsDepth(child, maxDepth - 1));
}

export function isBinding(value: unknown): value is { $bind: string } {
  return isObject(value) && typeof value.$bind === "string" && Object.keys(value).length === 1;
}

export function isAssetRef(value: unknown): value is { $asset: string } {
  return isObject(value) && typeof value.$asset === "string" && Object.keys(value).length === 1;
}

export function isInputRef(value: unknown): value is { $input: string } {
  return isObject(value) && typeof value.$input === "string" && Object.keys(value).length === 1;
}

export function isPositiveInteger(value: unknown): value is number {
  return Number.isInteger(value) && (value as number) > 0;
}

export function isObject(value: unknown): value is Record<string, any> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
