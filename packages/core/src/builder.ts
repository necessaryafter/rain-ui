import type { TypeSchema, TString, TInt, TLong, TBool, TItem, TList, TObject } from "./types";

// Compile-time schema builders (t.* API) with non-widening generics
export const t = {
    string: (): TString => ({ kind: "string" }),
    int: (): TInt => ({ kind: "int" }),
    long: (): TLong => ({ kind: "long" }),
    bool: (): TBool => ({ kind: "bool" }),
    item: (): TItem => ({ kind: "item" }),
    list: <Of extends TypeSchema>(of: Of): TList => ({ kind: "list", of }),
    object: <Fields extends Record<string, TypeSchema>>(fields: Fields): TObject => ({ kind: "object", fields }),
};

// Identity generics that preserve literal type inference (no widening)
export function defineProperties<P extends Record<string, TypeSchema>>(props: P): P {
    return props;
}

export function defineActions<A extends Record<string, TypeSchema>>(actions: A): A {
    return actions;
}

const SCREEN_DEFINITION_SYMBOL = Symbol("rain.screen");

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
