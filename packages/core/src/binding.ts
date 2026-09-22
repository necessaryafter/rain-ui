import type { TypeSchema } from "./types";

// Phantom-typed binding marker: { $bind: string } with implicit type info
export type Binding<T = unknown> = { readonly $bind: string; readonly __type?: T };

// Action reference: carries both the action id and payload schema type
export interface ActionRef<P extends TypeSchema = TypeSchema> {
    readonly __actionId: string;
    readonly __payloadSchema: P;
}

// Build-time property proxy: accessing p.property returns { $bind: "property" }
export function createPropertyProxy<Schema extends Record<string, TypeSchema>>(
    schema: Schema,
    scopePrefix: string[] = []
): { [K in keyof Schema]: Binding<unknown> } {
    return new Proxy({} as any, {
    get(_target, key: string | symbol) {
      if (typeof key !== "string") {
        return undefined;
      }

      const schemaEntry = schema[key as keyof Schema];
      if (!schemaEntry) {
        throw new Error(`Unknown property: ${key}`);
      }

      const fullPath = scopePrefix.length > 0 ? `${scopePrefix.join(".")}.${key}` : key;

      // For object-typed properties, return a recursive proxy for nested access
      if ((schemaEntry as any).kind === "object") {
        return createPropertyProxy((schemaEntry as any).fields, [...scopePrefix, key]);
      }

      return { $bind: fullPath };
    },
    });
}

// Build-time action proxy: accessing a["shop:buy"] returns ActionRef with id and schema
export function createActionProxy<A extends Record<string, TypeSchema>>(
    schema: A
): { [K in keyof A]: ActionRef<A[K]> } {
    const result: any = {};
    for (const id of Object.keys(schema)) {
    result[id] = {
      __actionId: id,
      __payloadSchema: schema[id],
    };
    }
    return result;
}
