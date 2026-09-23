import type { ComponentNode, Contract, TypeSchema } from "./types";

// Declared properties no binding in the screen touches, as paths like "listings[].sellerId". Only the outermost unused
// path is reported: an unused list or object is one warning, not one per field inside it.
export function findUnusedProperties(contract: Contract): string[] {
  const used = new Set<string>();
  collectBindings(contract.root, "", used);

  const unused: string[] = [];
  for (const [name, schema] of Object.entries(contract.properties)) {
    collectUnused(name, schema, used, unused);
  }

  return unused;
}

// Bindings inside a list's template are relative to the item, so they are prefixed with "<source>[].".
function collectBindings(node: ComponentNode, prefix: string, used: Set<string>): void {
  for (const value of Object.values(node.props)) {
    collectValueBindings(value, prefix, used);
  }

  const source = node.type === "list" ? bindingPath(node.props.source) : undefined;
  const childPrefix = source === undefined ? prefix : `${prefix}${source}[].`;

  for (const child of node.children) {
    collectBindings(child, childPrefix, used);
  }
}

// Payloads nest bindings inside literal objects and arrays.
function collectValueBindings(value: unknown, prefix: string, used: Set<string>): void {
  const path = bindingPath(value);
  if (path !== undefined) {
    used.add(prefix + path);
    return;
  }

  if (value === null || typeof value !== "object") return;

  for (const nested of Object.values(value)) {
    collectValueBindings(nested, prefix, used);
  }
}

function collectUnused(path: string, schema: TypeSchema, used: Set<string>, unused: string[]): void {
  if (!isTouched(path, used)) {
    unused.push(path);
    return;
  }

  if (schema.kind === "object") {
    for (const [name, field] of Object.entries(schema.fields)) {
      collectUnused(`${path}.${name}`, field, used, unused);
    }
    return;
  }

  if (schema.kind === "list" && schema.of.kind === "object") {
    for (const [name, field] of Object.entries(schema.of.fields)) {
      collectUnused(`${path}[].${name}`, field, used, unused);
    }
  }
}

function isTouched(path: string, used: Set<string>): boolean {
  for (const usedPath of used) {
    if (usedPath === path || usedPath.startsWith(`${path}.`) || usedPath.startsWith(`${path}[]`)) return true;
  }

  return false;
}

function bindingPath(value: unknown): string | undefined {
  if (value === null || typeof value !== "object") return undefined;

  const bind = (value as { $bind?: unknown }).$bind;
  return typeof bind === "string" ? bind : undefined;
}
