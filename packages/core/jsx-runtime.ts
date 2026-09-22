import type { Binding, ActionRef } from "./src/binding";
import type { TypeSchema } from "./src/types";

// Raw JSX tree node (before compilation to Contract JSON)
export interface RawNode {
    type: string;
    props: Record<string, unknown>;
    children: RawNode[];
}

// JSX runtime: generic, dumb — just construct nodes and call function components
export function jsx(type: string | Function, props: any, key?: any): RawNode | RawNode[] {
    if (typeof type === "function") {
    return type(props);
    }

    const { children, ...rest } = props ?? {};
    const normalizedChildren = children === undefined ? [] : Array.isArray(children) ? children : [children];

    return { type, props: rest, children: normalizedChildren };
}

export const jsxs = jsx;

export function Fragment(props: { children?: any }): RawNode[] {
    const children = props.children;
    if (Array.isArray(children)) {
    return children;
    }
    return children ? [children] : [];
}

// JSX.IntrinsicElements typing for v0 components

// Helper type: extract the payload schema from an ActionRef
type PayloadOf<Ref> = Ref extends ActionRef<infer P> ? P : never;

// Button props need cross-prop generic typing
type ButtonProps<P extends TypeSchema = TypeSchema> = {
    action: ActionRef<P>;
    payload?: any; // Relaxed for now; validateContract will check
    disabled?: boolean | Binding<boolean>;
    children?: any;
};

declare global {
    namespace JSX {
    interface IntrinsicElements {
      column: {
        gap?: number;
        padding?: number;
        align?: string;
        justify?: string;
        width?: string | number;
        height?: string | number;
        children?: any;
      };

      row: {
        gap?: number;
        padding?: number;
        align?: string;
        justify?: string;
        width?: string | number;
        height?: string | number;
        children?: any;
      };

      text: {
        value: string | Binding<string>;
        color?: string;
        align?: string;
        shadow?: boolean;
      };

      item: {
        value: Binding<"item">;
        size?: number;
      };

      button: ButtonProps;

      list: {
        source: Binding<"list">;
        children: (item: any) => RawNode;
      };
    }
    }
}

export {};
