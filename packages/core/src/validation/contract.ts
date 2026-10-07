import { Limits } from "./limits";
import type { TypeSchema } from "../contract/types";
import { NodeValidator } from "./nodes";
import { collectInputs } from "./node-rules";
import {
  FONT_TYPES, IMAGE_TYPES, OK, exceedsDepth, fail, isObject, matchesKind, type ValidationResult,
} from "./shared";

const ID_PATTERN = /^[a-z0-9_-]+:[a-z0-9_/-]+$/;
const KINDS = new Set(["string", "int", "long", "double", "bool", "item", "asset", "list", "object"]);
const HASH_PATTERN = /^[0-9a-f]{64}$/;
const DEFAULTABLE_KINDS = new Set(["string", "int", "long", "double", "bool"]);

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

  const assets = contract.assets ?? {};
  const assetNames = contract.assetNames ?? {};
  if (!isObject(assets)) return fail("UNKNOWN_SCHEMA_VERSION", "assets");
  if (!isObject(assetNames)) return fail("UNKNOWN_SCHEMA_VERSION", "assetNames");

  const assetsCheck = validateAssets(assets, assetNames);
  if (!assetsCheck.ok) return assetsCheck;

  const screenInputs = collectInputs(contract.root);
  const validator = new NodeValidator(contract.actions, assets, screenInputs);
  return validator.validateNode(contract.root, "root", contract.properties, 0, undefined, screenInputs);
}

// Table-wide checks come first, so a malformed or oversized table is reported as a whole before any single entry.
function validateAssets(assets: Record<string, unknown>, assetNames: Record<string, unknown>): ValidationResult {
  const hashes = Object.keys(assets);
  if (!hashes.every((hash) => HASH_PATTERN.test(hash))) return fail("INVALID_ASSET_HASH", "assets");
  if (hashes.length > Limits.MAX_ASSETS) return fail("LIMIT_EXCEEDED", "assets");

  const totalBytes = Object.values(assets).reduce<number>((sum, info: any) => sum + (Number(info?.bytes) || 0), 0);
  if (totalBytes > Limits.MAX_TOTAL_ASSET_BYTES) return fail("LIMIT_EXCEEDED", "assets");

  for (const [hash, info] of Object.entries(assets)) {
    const result = validateAssetInfo(info, `assets.${hash}`);
    if (!result.ok) return result;
  }

  for (const [name, hash] of Object.entries(assetNames)) {
    if (typeof hash !== "string" || !(hash in assets)) return fail("UNDECLARED_ASSET", `assetNames.${name}`);
  }

  return OK;
}

function validateAssetInfo(info: unknown, path: string): ValidationResult {
  if (!isObject(info)) return fail("UNSUPPORTED_ASSET", path);

  const { type, bytes } = info;
  if (!IMAGE_TYPES.has(type) && !FONT_TYPES.has(type)) return fail("UNSUPPORTED_ASSET", path);
  if (!isCount(bytes)) return fail("UNSUPPORTED_ASSET", path);
  if (bytes > Limits.MAX_ASSET_BYTES) return fail("LIMIT_EXCEEDED", path);
  if (!IMAGE_TYPES.has(type)) return OK;

  const { width, height, frames } = info;
  if (!isCount(width) || !isCount(height) || width === 0 || height === 0) return fail("UNSUPPORTED_ASSET", path);
  if (width > Limits.MAX_IMAGE_DIMENSION || height > Limits.MAX_IMAGE_DIMENSION) return fail("LIMIT_EXCEEDED", path);
  if (type !== "image/gif") return OK;

  if (!isCount(frames) || frames === 0) return fail("UNSUPPORTED_ASSET", path);
  if (frames > Limits.MAX_GIF_FRAMES) return fail("LIMIT_EXCEEDED", path);
  if (width * height * 4 * frames > Limits.MAX_GIF_DECODED_BYTES) return fail("LIMIT_EXCEEDED", path);

  return OK;
}

function isCount(value: unknown): value is number {
  return Number.isInteger(value) && (value as number) >= 0;
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
