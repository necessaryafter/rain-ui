import type { TypeSchema } from "./types";

// Phantom-typed binding marker: { $bind: string } with implicit type info
export type Binding<T = unknown> = { readonly $bind: string; readonly __type?: T };

// Action reference: carries both the action id and payload schema type
export interface ActionRef<P extends TypeSchema = TypeSchema> {
    readonly __actionId: string;
    readonly __payloadSchema: P;
}

// Keys that JS machinery reads on any object (await, JSON.stringify, inspection). Answering them with undefined keeps
// the proxies inert instead of throwing from inside the runtime.
const INERT_KEYS = new Set(["then", "toJSON", "constructor", "$$typeof"]);

function misuseError(path: string, detail: string): Error {
  return new Error(
    `"${path}" is a binding, not a value: ${detail}. In render a binding only marks where the data goes; ` +
    `send the value ready from the server as a property.`,
  );
}

function rejectPrimitive(path: string): () => never {
  return () => {
    throw misuseError(path, "it cannot be converted to a string or number, compared or concatenated");
  };
}

// Build-time property proxy: p.name returns a binding for "name", and p.owner returns a proxy that is itself a binding
// for "owner" while also exposing its fields (p.owner.name).
export function createPropertyProxy<Schema extends Record<string, TypeSchema>>(
  schema: Schema,
  prefix: string[] = [],
): { [K in keyof Schema]: Binding<unknown> } {
  const path = prefix.join(".");

  return new Proxy({} as { [K in keyof Schema]: Binding<unknown> }, {
    get(_, key: string | symbol) {
      if (key === Symbol.toPrimitive) return rejectPrimitive(path || "properties");
      if (typeof key !== "string") return undefined;
      if (key === "$bind") return prefix.length > 0 ? path : undefined;
      if (INERT_KEYS.has(key)) return undefined;

      const entry = schema[key];
      if (!entry) {
        throw new Error(`Unknown property: ${[...prefix, key].join(".")}`);
      }

      if (entry.kind === "object") {
        return createPropertyProxy(entry.fields, [...prefix, key]);
      }

      return createLeafBinding([...prefix, key].join("."), entry);
    },
    has(_, key) {
      return key === "$bind" && prefix.length > 0;
    },
  });
}

function createLeafBinding(path: string, schema: TypeSchema): Binding<unknown> {
  return new Proxy({ $bind: path } as Binding<unknown>, {
    get(target, key: string | symbol) {
      if (key === "$bind") return target.$bind;
      if (key === Symbol.toPrimitive || key === "valueOf" || key === "toString") return rejectPrimitive(path);
      if (typeof key !== "string" || INERT_KEYS.has(key)) return undefined;

      if (schema.kind === "list") {
        throw misuseError(path, `a list has no "${key}" at build time; render its items with <list>`);
      }

      throw misuseError(path, `a ${schema.kind} has no field "${key}"`);
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
