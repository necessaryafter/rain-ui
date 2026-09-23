import type { TypeSchema } from "./types";

export interface ResolvedPath {
  schema: TypeSchema;
  // True when any segment of the path may be absent at runtime, so the value itself may be absent.
  optional: boolean;
}

// Resolves a dotted binding path ("bidData.currentBid") against a scope, descending through object fields.
export function resolvePath(scope: Record<string, TypeSchema>, path: string): ResolvedPath | undefined {
  const [first, ...rest] = path.split(".");

  let schema = scope[first];
  if (!schema) return undefined;

  let optional = schema.optional === true;
  for (const segment of rest) {
    if (schema.kind !== "object") return undefined;

    schema = schema.fields[segment];
    if (!schema) return undefined;

    optional ||= schema.optional === true;
  }

  return { schema, optional };
}

// The fields a list's item template can bind, or undefined when the source is not a list of objects.
export function itemScopeOf(scope: Record<string, TypeSchema>, sourcePath: string): Record<string, TypeSchema> | undefined {
  const source = resolvePath(scope, sourcePath);
  if (!source || source.schema.kind !== "list") return undefined;

  const item = source.schema.of;
  if (item.kind !== "object") return undefined;

  return item.fields;
}
