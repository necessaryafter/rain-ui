import * as crypto from "crypto";

import type { RawNode } from "../jsx-runtime";
import { createActionProxy, createPropertyProxy } from "./binding";
import type { ScreenDefinition } from "./builder";
import { itemScopeOf } from "./path";
import type { AssetInfo, AssetRef, ComponentNode, Contract, TypeSchema } from "./types";

type Scope = Record<string, TypeSchema>;

// Every asset a screen references, keyed by hash, collected while its props are resolved.
type AssetTable = Map<string, AssetInfo>;

export interface CompiledScreen {
  id: string;
  contract: Contract;
  json: string;
  sha256: string;
}

// Runs render once with binding proxies and turns the JSX tree into the contract, plus its canonical JSON and hash.
export function compileScreen(screenDef: ScreenDefinition): CompiledScreen {
  const { id, properties, actions, render } = screenDef;
  const assets: AssetTable = new Map();

  const rawTree = render(createPropertyProxy(properties), createActionProxy(actions));
  const contract: Contract = {
    schemaVersion: 0,
    id,
    properties,
    actions,
    root: compileNode(rawTree as RawNode, properties, assets),
  };

  const assetNames = registerNamedAssets(screenDef.assets ?? {}, assets);
  if (Object.keys(assetNames).length > 0) {
    contract.assetNames = assetNames;
  }

  // Absent rather than empty, so screens without assets keep the contract (and hash) they had before assets existed.
  if (assets.size > 0) {
    contract.assets = Object.fromEntries(assets);
  }

  const json = JSON.stringify(canonicalize(contract));
  const sha256 = crypto.createHash("sha256").update(json).digest("hex");

  return { id, contract, json, sha256 };
}

function registerNamedAssets(named: Record<string, AssetRef>, assets: AssetTable): Record<string, string> {
  const names: Record<string, string> = {};
  for (const [name, ref] of Object.entries(named)) {
    names[name] = registerAsset(ref, assets);
  }

  return names;
}

function registerAsset(ref: AssetRef, assets: AssetTable): string {
  const { $asset, ...info } = ref;
  assets.set($asset, info);

  return $asset;
}

function compileNode(node: RawNode, scope: Scope, assets: AssetTable): ComponentNode {
  if (node.type === "list") return compileList(node, scope, assets);

  const { fallback, ...props } = node.props;
  const children = compileChildren(node.children, scope, assets);

  // <show fallback={...}> is written as a prop but lives in the contract as a trailing "fallback" child node.
  if (node.type === "show" && fallback !== undefined) {
    children.push({
      type: "fallback",
      props: {},
      children: compileChildren([fallback], scope, assets),
    });
  }

  return {
    type: node.type,
    props: resolveProps(node.type === "show" ? props : node.props, assets),
    children,
  };
}

// A list's only child is a function: it is called once with a proxy of the item's fields to get the row template.
function compileList(node: RawNode, scope: Scope, assets: AssetTable): ComponentNode {
  const props = resolveProps(node.props, assets);
  const source = props.source as { $bind?: unknown } | undefined;
  const template = node.children[0] as unknown;

  const itemScope = typeof source?.$bind === "string" ? itemScopeOf(scope, source.$bind) : undefined;
  if (!itemScope || typeof template !== "function") {
    return { type: "list", props, children: [] };
  }

  return {
    type: "list",
    props,
    children: [compileNode(template(createPropertyProxy(itemScope)) as RawNode, itemScope, assets)],
  };
}

// Fragments and arrays are flattened, and null/false/undefined are dropped, so build-time conditions such as
// {SHOW_DEBUG && <text value="debug" />} work.
function compileChildren(children: unknown[], scope: Scope, assets: AssetTable): ComponentNode[] {
  return children
    .flat(Infinity)
    .filter((child) => child !== null && child !== undefined && child !== false)
    .map((child) => compileNode(child as RawNode, scope, assets));
}

function resolveProps(props: Record<string, unknown>, assets: AssetTable): Record<string, unknown> {
  const resolved: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(props)) {
    resolved[key] = resolveValue(value, assets);
  }

  return resolved;
}

// Converts bindings, asset imports and action references to their JSON form and passes literals through.
function resolveValue(value: unknown, assets: AssetTable): unknown {
  if (value === null || typeof value !== "object") return value;
  if (Array.isArray(value)) return value.map((item) => resolveValue(item, assets));

  // $bind first: a binding proxy throws on any other key, so it must be recognized before anything else is read.
  const record = value as Record<string, unknown>;
  if (typeof record.$bind === "string") return { $bind: record.$bind };
  if (typeof record.$asset === "string") return { $asset: registerAsset(record as unknown as AssetRef, assets) };
  if (typeof record.__actionId === "string") return record.__actionId;

  const resolved: Record<string, unknown> = {};
  for (const [key, field] of Object.entries(record)) {
    if (key.startsWith("__")) continue;

    resolved[key] = resolveValue(field, assets);
  }

  return resolved;
}

function canonicalize(value: unknown): unknown {
  if (value === null || typeof value !== "object") return value;
  if (Array.isArray(value)) return value.map(canonicalize);

  const sorted: Record<string, unknown> = {};
  for (const key of Object.keys(value).sort()) {
    sorted[key] = canonicalize((value as Record<string, unknown>)[key]);
  }

  return sorted;
}

export { createActionProxy, createPropertyProxy } from "./binding";
