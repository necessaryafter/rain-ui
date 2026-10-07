import { itemScopeOf, resolvePath } from "../contract/path";
import { OK, fail, isBinding, isPositiveInteger, matchesKind, type Scope, type ValidationResult } from "./shared";
import { validateBinding } from "./values";

export const MATCHABLE_KINDS = new Set(["string", "int", "long", "bool"]);

export const ALLOWED_PROPS: Record<string, Set<string>> = {
  column: layoutProps(),
  row: layoutProps(),
  text: new Set([
    "value", "color", "font", "align", "shadow", "fontSize", "fontWeight",
    "textAlign", "lineHeight", "letterSpacing", "strokeColor", "strokeWidth",
  ]),
  image: new Set([...layoutProps(), "src"]),
  item: new Set(["value", "size"]),
  button: new Set([...layoutProps(), "action", "payload", "disabled"]),
  list: new Set(["source"]),
  show: new Set(["when"]),
  match: new Set(["value"]),
  case: new Set(["is"]),
  default: new Set(),
  fallback: new Set(),
  box: layoutProps(),
  stack: layoutProps(),
  grid: new Set([...layoutProps(), "cellWidth", "cellHeight", "columns"]),
  scroll: new Set([...layoutProps(), "direction"]),
  input: new Set([
    "id", "value", "placeholder", "multiline", "maxLength", "width", "height", "minWidth",
    "minHeight", "maxWidth", "maxHeight", "margin", "position", "top", "right", "bottom", "left",
  ]),
  tabs: new Set(["defaultTab"]),
  tab: new Set(["id", "label"]),
};

function layoutProps(): Set<string> {
  return new Set([
    "width", "height", "minWidth", "minHeight", "maxWidth", "maxHeight", "padding", "margin",
    "gap", "position", "top", "right", "bottom", "left", "align", "justify", "grow",
    "shrink", "background", "opacity", "overflow", "zIndex", "borderWidth", "borderColor",
    "borderRadius", "rotate", "scale", "scaleX", "scaleY", "skewX", "skewY", "shape",
  ]);
}

export const SLOT_PARENTS: Record<string, string> = {
  case: "match",
  default: "match",
  fallback: "show",
  tab: "tabs",
};

export function validateKey(key: unknown, path: string, scope: Scope): ValidationResult {
  if (key === undefined || typeof key === "string" || Number.isInteger(key)) return OK;
  if (!isBinding(key)) return fail("INVALID_KEY", path);

  const resolved = resolvePath(scope, key.$bind);
  if (!resolved) return fail("UNDECLARED_BINDING", path);
  if (!["string", "int", "long"].includes(resolved.schema.kind)) return fail("BINDING_TYPE_MISMATCH", path);

  return OK;
}

export function validateGridProps(node: any, path: string, scope: Scope): ValidationResult {
  const props = node.props ?? {};

  for (const key of ["cellWidth", "cellHeight"]) {
    const result = validateGridDimension(props[key], `${path}.props.${key}`, scope);
    if (!result.ok) return result;
  }

  if (props.columns !== undefined) {
    const result = validateGridDimension(props.columns, `${path}.props.columns`, scope);
    if (!result.ok) return result;
  }
  if (props.columns === undefined && !hasFiniteWidth(node)) {
    return fail("INVALID_LAYOUT", `${path}.props.columns`);
  }

  return OK;
}

export function validateInputProps(props: Record<string, unknown>, path: string): ValidationResult {
  if (typeof props.id !== "string" || props.id.length === 0) {
    return fail("INVALID_PROP_TYPE", `${path}.props.id`);
  }
  if (props.maxLength !== undefined && (!isPositiveInteger(props.maxLength) || props.maxLength > 4096)) {
    return fail("LIMIT_EXCEEDED", `${path}.props.maxLength`);
  }

  return OK;
}

export function validateTabs(children: any[], defaultTab: unknown, path: string): ValidationResult {
  const ids = new Set<string>();

  for (let i = 0; i < children.length; i++) {
    const childPath = `${path}.children[${i}]`;
    if (children[i]?.type !== "tab") return fail("MISPLACED_COMPONENT", childPath);

    const id = children[i]?.props?.id;
    if (typeof id !== "string" || id.length === 0) {
      return fail("INVALID_PROP_TYPE", `${childPath}.props.id`);
    }
    if (children[i]?.props?.label === undefined) {
      return fail("INVALID_PROP_TYPE", `${childPath}.props.label`);
    }
    if (ids.has(id)) return fail("DUPLICATE_TAB", `${childPath}.props.id`);

    ids.add(id);
  }

  if (typeof defaultTab !== "string" || !ids.has(defaultTab)) {
    return fail("INVALID_PROP_VALUE", `${path}.props.defaultTab`);
  }

  return OK;
}

export function validateMatchSlots(node: any, children: any[], path: string, scope: Scope): ValidationResult {
  const matched = isBinding(node.props?.value) ? resolvePath(scope, node.props.value.$bind) : undefined;
  const seen = new Set<unknown>();

  for (let i = 0; i < children.length; i++) {
    const child = children[i];
    const childPath = `${path}.children[${i}]`;

    if (child?.type === "default") {
      if (i !== children.length - 1) return fail("MISPLACED_COMPONENT", childPath);
      continue;
    }

    if (child?.type !== "case") return fail("MISPLACED_COMPONENT", childPath);

    const literal = child.props?.is;
    const literalPath = `${childPath}.props.is`;
    if (matched && !matchesKind(literal, matched.schema.kind)) return fail("INVALID_PROP_TYPE", literalPath);
    if (seen.has(literal)) return fail("DUPLICATE_CASE", literalPath);

    seen.add(literal);
  }

  return OK;
}

export function listItemScope(node: any, scope: Scope): Scope | undefined {
  const source = node.props?.source;
  if (!isBinding(source)) return undefined;

  return itemScopeOf(scope, source.$bind);
}

function hasFiniteWidth(node: any): boolean {
  return typeof node.props?.width === "number" || node.props?.width === "fill"
    || isBinding(node.props?.width);
}

function validateGridDimension(value: unknown, path: string, scope: Scope): ValidationResult {
  if (isPositiveInteger(value)) return OK;
  if (isBinding(value)) {
    return validateBinding(value, path, scope, (kind) => kind === "int" || kind === "long");
  }

  return fail("INVALID_PROP_VALUE", path);
}

export function collectInputs(node: any): Set<string> {
  const ids = new Set<string>();
  const visit = (current: any) => {
    if (!current || typeof current !== "object") return;
    if (current.type === "list") return;
    if (current.type === "input" && typeof current.props?.id === "string") ids.add(current.props.id);
    for (const child of current.children ?? []) visit(child);
  };
  visit(node);
  return ids;
}

export function containsInput(node: any): boolean {
  if (node?.type === "input") return true;
  return Array.isArray(node?.children) && node.children.some(containsInput);
}

export function containsClip(node: any): boolean {
  if (node?.type === "scroll" || node?.props?.overflow === "hidden") return true;
  return Array.isArray(node?.children) && node.children.some(containsClip);
}
