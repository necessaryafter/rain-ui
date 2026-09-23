import type { TBool, TDouble, TInt, TItem, TList, TLong, TObject, TString, TypeSchema } from "./types";

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

export interface ScreenDefinition<P extends Record<string, TypeSchema> = Record<string, TypeSchema>, A extends Record<string, TypeSchema> = Record<string, TypeSchema>> {
    id: string;
    properties: P;
    actions: A;
    render: (p: any, a: any) => any;
    [SCREEN_DEFINITION_SYMBOL]: true;
}

export function defineScreen<
    P extends Record<string, TypeSchema>,
    A extends Record<string, TypeSchema>,
>(def: {
    id: string;
    properties: P;
    actions: A;
    render: (p: any, a: any) => any;
}): ScreenDefinition<P, A> {
    return {
    ...def,
    [SCREEN_DEFINITION_SYMBOL]: true,
    } as ScreenDefinition<P, A>;
}

export function isScreenDefinition(x: unknown): x is ScreenDefinition {
    return (
    typeof x === "object" &&
    x !== null &&
    SCREEN_DEFINITION_SYMBOL in x &&
    (x as Record<symbol, unknown>)[SCREEN_DEFINITION_SYMBOL] === true
    );
}
