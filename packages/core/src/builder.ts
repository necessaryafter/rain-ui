import type { AssetRef, TAsset, TBool, TDouble, TInt, TItem, TList, TLong, TObject, TString, TypeSchema } from "./types";

export type Optionable<S extends TypeSchema> = S & {
  optional(): S & { optional: true };
};

export type Defaultable<S extends TypeSchema, V> = Optionable<S> & {
  default(value: V): S & { default: V };
};

// The modifiers are non-enumerable, so they never reach the serialized contract or its hash. They are terminal and
// return a plain schema: the method names are also the serialized field names, so a modified schema cannot keep them.
function optionable<S extends TypeSchema>(schema: S): Optionable<S> {
  return Object.defineProperties({ ...schema }, {
    optional: { value: () => ({ ...schema, optional: true }) },
  }) as Optionable<S>;
}

function defaultable<S extends TypeSchema, V>(schema: S): Defaultable<S, V> {
  return Object.defineProperties(optionable(schema), {
    default: { value: (value: V) => ({ ...schema, default: value }) },
  }) as Defaultable<S, V>;
}

export const t = {
  string: () => defaultable<TString, string>({ kind: "string" }),
  int: () => defaultable<TInt, number>({ kind: "int" }),
  long: () => defaultable<TLong, number>({ kind: "long" }),
  double: () => defaultable<TDouble, number>({ kind: "double" }),
  bool: () => defaultable<TBool, boolean>({ kind: "bool" }),
  item: () => optionable<TItem>({ kind: "item" }),
  asset: () => optionable<TAsset>({ kind: "asset" }),
  list: <Of extends TypeSchema>(of: Of) => optionable<TList>({ kind: "list", of }),
  object: <Fields extends Record<string, TypeSchema>>(fields: Fields) => optionable<TObject>({ kind: "object", fields }),
};

// Identity generics that preserve literal type inference (no widening)
export function defineProperties<P extends Record<string, TypeSchema>>(props: P): P {
    return props;
}

export function defineActions<A extends Record<string, TypeSchema>>(actions: A): A {
    return actions;
}

// Symbol.for so a screen built against another copy of @rain-ui/core (e.g. the CLI's) is still recognized.
const SCREEN_DEFINITION_SYMBOL = Symbol.for("rain.screen");

type Schemas = Record<string, TypeSchema>;

interface ScreenOptions<P extends Schemas, A extends Schemas> {
  id: string;
  properties: P;
  actions: A;
  // Assets the server can pick at runtime through a t.asset() property, by these names.
  assets?: Record<string, AssetRef>;
  render: (p: any, a: any) => any;
}

export interface ScreenDefinition<P extends Schemas = Schemas, A extends Schemas = Schemas> extends ScreenOptions<P, A> {
  [SCREEN_DEFINITION_SYMBOL]: true;
}

export function defineScreen<P extends Schemas, A extends Schemas>(def: ScreenOptions<P, A>): ScreenDefinition<P, A> {
  return {
    ...def,
    [SCREEN_DEFINITION_SYMBOL]: true,
  };
}

export function isScreenDefinition(x: unknown): x is ScreenDefinition {
  if (typeof x !== "object" || x === null) return false;

  return (x as Record<symbol, unknown>)[SCREEN_DEFINITION_SYMBOL] === true;
}
