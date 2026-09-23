import type { ScreenDefinition } from "./builder";
import type { ComponentNode, Contract, TypeSchema } from "./types";
import type { RawNode } from "../jsx-runtime";
import { createPropertyProxy, createActionProxy } from "./binding";
import * as crypto from "crypto";

export interface CompiledScreen {
    id: string;
    contract: Contract;
    sha256: string;
}

// Compile a screen definition into a Contract JSON with sha256 hash
export function compileScreen(screenDef: ScreenDefinition): CompiledScreen {
    const { id, properties, actions, render } = screenDef;

    // Build proxies for build-time execution
    const propertyProxy = createPropertyProxy(properties);
    const actionProxy = createActionProxy(actions);

    // Execute render once to get the raw JSX tree
    const rawTree = render(propertyProxy, actionProxy);

    // Walk the raw tree and compile to ComponentNode tree
    const rootComponent = compileNode(rawTree as RawNode, properties);

    // Build the full contract
    const contract: Contract = {
    	schemaVersion: 0,
    	id,
    	properties,
    	actions,
    	root: rootComponent,
    };

    // Serialize to canonical JSON (sorted keys) and compute hash
    const contractJson = JSON.stringify(canonicalize(contract));
    const sha256 = crypto.createHash("sha256").update(contractJson).digest("hex");

    return { id, contract, sha256 };
}

// Walk raw JSX tree and compile to ComponentNode, resolving bindings/ActionRef
function compileNode(node: RawNode, scope: Record<string, TypeSchema>): ComponentNode {
    const { type, props, children } = node;

    // Resolve props: convert Binding and ActionRef to their JSON form
    const resolvedProps: Record<string, unknown> = {};
    for (const [key, value] of Object.entries(props)) {
    	resolvedProps[key] = resolveValue(value, scope);
    }

    // Compile children
    const compiledChildren: ComponentNode[] = [];

    // Special handling for list: child is a function, not a node
    if (type === "list") {
    const sourceBinding = resolvedProps["source"];
    if (sourceBinding && typeof sourceBinding === "object" && "$bind" in sourceBinding) {
      const bindPath = (sourceBinding as any).$bind;
      const sourceSchema = scope[bindPath];

      if (sourceSchema && (sourceSchema as any).kind === "list") {
        const listOf = (sourceSchema as any).of;

        if (listOf && (listOf as any).kind === "object") {
          const itemScope = (listOf as any).fields;
          // Call the function child with item scope proxy
          const itemProxy = createPropertyProxy(itemScope);
          const templateNode = (children[0] as Function)(itemProxy);

          compiledChildren.push(compileNode(templateNode as RawNode, itemScope));
        }
      }
    }
    } else {
		for (const child of children) {
			compiledChildren.push(compileNode(child as RawNode, scope));
    	}
	}

    return {
    	type,
    	props: resolvedProps,
    	children: compiledChildren,
    };
}

// Resolve a value: convert Binding, ActionRef to JSON form, pass through others
function resolveValue(value: unknown, scope: Record<string, TypeSchema>): unknown {
    if (value === null || value === undefined) {
    return value;
    }

    if (typeof value === "object") {
    const obj = value as Record<string, unknown>;

    // Check for ActionRef: has __actionId
    if ("__actionId" in obj && typeof obj.__actionId === "string") {
      return obj.__actionId;
    }

    // Check for Binding: has $bind
    if ("$bind" in obj && typeof obj.$bind === "string") {
      return { $bind: obj.$bind };
    }

    // Check for plain object (props payload, etc.)
    if (Array.isArray(value)) {
      return (value as unknown[]).map((item) => resolveValue(item, scope));
    }

    // Recursively resolve object fields
    const resolved: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(obj)) {
      if (!k.startsWith("__")) {
        // Skip phantom/internal fields
        resolved[k] = resolveValue(v, scope);
      }
    }
    return resolved;
    }

    return value;
}

// Recursively sort object keys for canonical JSON
function canonicalize(obj: any): any {
    if (obj === null || typeof obj !== "object") {
    	return obj;
    }

    if (Array.isArray(obj)) {
    	return obj.map(canonicalize);
    }

    const sorted: Record<string, any> = {};
    for (const key of Object.keys(obj).sort()) {
    	sorted[key] = canonicalize(obj[key]);
    }
    return sorted;
}

// Re-export for convenience
export { createPropertyProxy, createActionProxy } from "./binding";
