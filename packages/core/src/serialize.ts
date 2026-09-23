import * as crypto from "crypto";

import type { RawNode } from "../jsx-runtime";
import { createActionProxy, createPropertyProxy } from "./binding";
import type { ScreenDefinition } from "./builder";
import { itemScopeOf } from "./path";
import type { ComponentNode, Contract, TypeSchema } from "./types";

export interface CompiledScreen {
  id: string;
  contract: Contract;
  json: string;
  sha256: string;
}

// Runs render once with binding proxies and turns the JSX tree into the contract, plus its canonical JSON and hash.
export function compileScreen(screenDef: ScreenDefinition): CompiledScreen {
  const { id, properties, actions, render } = screenDef;

  const rawTree = render(createPropertyProxy(properties), createActionProxy(actions));
  const contract: Contract = {
    schemaVersion: 0,
    id,
    properties,
    actions,
    root: compileNode(rawTree as RawNode, properties),
  };

  const json = JSON.stringify(canonicalize(contract));
  const sha256 = crypto.createHash("sha256").update(json).digest("hex");

  return { id, contract, json, sha256 };
}

function compileNode(node: RawNode, scope: Record<string, TypeSchema>): ComponentNode {
  if (node.type === "list") return compileList(node, scope);

  const { fallback, ...props } = node.props;
  const children = compileChildren(node.children, scope);

  // <show fallback={...}> is written as a prop but lives in the contract as a trailing "fallback" child node.
  if (node.type === "show" && fallback !== undefined) {
    children.push({
      type: "fallback",
      props: {},
      children: compileChildren([fallback], scope),
    });
  }

  return {
    type: node.type,
    props: resolveProps(node.type === "show" ? props : node.props),
    children,
  };
}

// A list's only child is a function: it is called once with a proxy of the item's fields to get the row template.
function compileList(node: RawNode, scope: Record<string, TypeSchema>): ComponentNode {
  const props = resolveProps(node.props);
  const source = props.source as { $bind?: unknown } | undefined;
  const template = node.children[0] as unknown;

  const itemScope = typeof source?.$bind === "string" ? itemScopeOf(scope, source.$bind) : undefined;
  if (!itemScope || typeof template !== "function") {
    return { type: "list", props, children: [] };
  }

  return {
    type: "list",
    props,
    children: [compileNode(template(createPropertyProxy(itemScope)) as RawNode, itemScope)],
  };
}

// Fragments and arrays are flattened, and null/false/undefined are dropped, so build-time conditions such as
// {SHOW_DEBUG && <text value="debug" />} work.
function compileChildren(children: unknown[], scope: Record<string, TypeSchema>): ComponentNode[] {
  return children
    .flat(Infinity)
    .filter((child) => child !== null && child !== undefined && child !== false)
    .map((child) => compileNode(child as RawNode, scope));
}

function resolveProps(props: Record<string, unknown>): Record<string, unknown> {
  const resolved: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(props)) {
    resolved[key] = resolveValue(value);
  }

  return resolved;
}

// Converts bindings and action references to their JSON form and passes literals through.
function resolveValue(value: unknown): unknown {
  if (value === null || typeof value !== "object") return value;
  if (Array.isArray(value)) return value.map(resolveValue);

  // $bind first: a binding proxy throws on any other key, so it must be recognized before anything else is read.
  const record = value as Record<string, unknown>;
  if (typeof record.$bind === "string") return { $bind: record.$bind };
  if (typeof record.__actionId === "string") return record.__actionId;

  const resolved: Record<string, unknown> = {};
  for (const [key, field] of Object.entries(record)) {
    if (key.startsWith("__")) continue;

    resolved[key] = resolveValue(field);
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
