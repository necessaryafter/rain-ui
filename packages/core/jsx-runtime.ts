/// <reference path="./assets.d.ts" />
import type { Binding, ActionRef } from "./src/authoring/binding";
import type { Polygon } from "./src/authoring/polygon";
import type { AssetRef, TypeSchema } from "./src/contract/types";

// Raw JSX tree node (before compilation to Contract JSON)
export interface RawNode {
  type: string;
  props: Record<string, unknown>;
  children: RawNode[];
  key?: unknown;
}

// JSX runtime: generic, dumb — just construct nodes and call function components
export function jsx(type: string | Function, props: any, key?: any): RawNode | RawNode[] {
  if (typeof type === "function") {
    const result = type(props);
    if (key === undefined || Array.isArray(result)) return result;

    return { ...result, key };
  }

  const { children, ...rest } = props ?? {};
  const normalizedChildren = children === undefined ? [] : Array.isArray(children) ? children : [children];

  return { type, props: rest, children: normalizedChildren, ...(key === undefined ? {} : { key }) };
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
type ButtonProps<P extends TypeSchema = TypeSchema> = LayoutProps & {
  action: ActionRef<P>;
  payload?: any; // Relaxed for now; validateContract will check
  disabled?: boolean | Binding<boolean>;
  children?: any;
};

type Size = number | "fit" | "fill" | Binding<number>;
type NumberValue = number | Binding<number>;
type Color = string | Binding<string>;
type Spacing = number | { top?: number; right?: number; bottom?: number; left?: number };
type LayoutProps = {
  width?: Size;
  height?: Size;
  minWidth?: NumberValue;
  minHeight?: NumberValue;
  maxWidth?: NumberValue;
  maxHeight?: NumberValue;
  padding?: Spacing;
  margin?: Spacing;
  gap?: NumberValue;
  position?: "relative" | "absolute";
  top?: NumberValue;
  right?: NumberValue;
  bottom?: NumberValue;
  left?: NumberValue;
  align?: "start" | "center" | "end" | "stretch";
  justify?: "start" | "center" | "end" | "space-between";
  grow?: NumberValue;
  shrink?: NumberValue;
  background?: Color;
  opacity?: NumberValue;
  overflow?: "visible" | "hidden";
  zIndex?: NumberValue;
  borderWidth?: NumberValue;
  borderColor?: Color;
  borderRadius?: NumberValue;
  rotate?: NumberValue;
  scale?: NumberValue;
  scaleX?: NumberValue;
  scaleY?: NumberValue;
  skewX?: NumberValue;
  skewY?: NumberValue;
  shape?: Polygon;
  children?: any;
};

declare global {
  namespace JSX {
    interface IntrinsicElements {
      column: LayoutProps;

      row: LayoutProps;
      box: LayoutProps;
      stack: LayoutProps;
      grid: LayoutProps & { cellWidth: NumberValue; cellHeight: NumberValue; columns?: NumberValue };
      scroll: LayoutProps & { direction?: "vertical" | "horizontal" | "both" };

      text: {
        value: string | Binding<string> | Binding<number>;
        color?: Color;
        font?: AssetRef;
        align?: "left" | "center" | "right" | "start" | "end";
        shadow?: boolean;
        fontSize?: NumberValue;
        fontWeight?: "normal" | "bold";
        textAlign?: "start" | "center" | "end";
        lineHeight?: NumberValue;
        letterSpacing?: NumberValue;
        strokeColor?: Color;
        strokeWidth?: NumberValue;
      };

      item: {
        value: Binding<"item">;
        size?: number;
      };

      image: LayoutProps & { src: AssetRef | Binding<unknown> };

      button: ButtonProps;
      input: {
        id: string;
        value?: string | Binding<string>;
        placeholder?: string | Binding<string>;
        multiline?: boolean;
        maxLength?: number;
        width?: Size;
        height?: Size;
        minWidth?: NumberValue;
        minHeight?: NumberValue;
        maxWidth?: NumberValue;
        maxHeight?: NumberValue;
        margin?: Spacing;
        position?: "relative" | "absolute";
        top?: NumberValue;
        right?: NumberValue;
        bottom?: NumberValue;
        left?: NumberValue;
      };
      tabs: { defaultTab: string; children?: any };
      tab: { id: string; label: string | Binding<string>; children?: any };

      list: {
        source: Binding<"list">;
        children: (item: any) => RawNode;
      };

      show: {
        when: Binding<unknown>;
        fallback?: any;
        children?: any;
      };

      match: {
        value: Binding<unknown>;
        children?: any;
      };

      case: {
        is: string | number | boolean;
        children?: any;
      };

      default: {
        children?: any;
      };
    }
  }
}

export {};
