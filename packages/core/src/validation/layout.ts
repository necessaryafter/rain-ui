import { OK, fail, isObject, type Scope, type ValidationResult } from "./shared";
import { validateBinding, validateColor } from "./values";

function isSize(value: unknown): boolean {
  return value === "fit" || value === "fill";
}

export function validateLayoutProps(node: any, path: string, scope: Scope): ValidationResult {
  const props = node.props ?? {};

  for (const [key, value] of Object.entries(props)) {
    const propPath = `${path}.props.${key}`;
    const result = validateLayoutProp(node.type, key, value, propPath, scope);
    if (!result.ok) return result;
  }

  for (const axis of [["minWidth", "maxWidth"], ["minHeight", "maxHeight"]] as const) {
    if (typeof props[axis[0]] === "number" && typeof props[axis[1]] === "number"
      && props[axis[0]] > props[axis[1]]) {
      return fail("INVALID_LAYOUT", `${path}.props.${axis[1]}`);
    }
  }

  for (const axis of [["left", "right"], ["top", "bottom"]] as const) {
    if (props[axis[0]] !== undefined && props[axis[1]] !== undefined) {
      return fail("INVALID_PROP_COMBINATION", `${path}.props.${axis[1]}`);
    }
  }

  if (props.position !== undefined && props.position !== "relative" && props.position !== "absolute") {
    return fail("INVALID_PROP_VALUE", `${path}.props.position`);
  }
  if (props.overflow !== undefined && props.overflow !== "visible" && props.overflow !== "hidden") {
    return fail("INVALID_PROP_VALUE", `${path}.props.overflow`);
  }
  if (props.shape !== undefined) {
    if (props.borderRadius !== undefined) return fail("INVALID_PROP_COMBINATION", `${path}.props.borderRadius`);
    if (!isValidPolygon(props.shape)) return fail("INVALID_POLYGON", `${path}.props.shape`);
  }
  if (props.overflow === "hidden" && (hasNonZero(props.rotate) || hasNonZero(props.skewX) || hasNonZero(props.skewY))) {
    const transform = hasNonZero(props.rotate) ? "rotate" : hasNonZero(props.skewX) ? "skewX" : "skewY";
    return fail("UNSUPPORTED_COMBINATION", `${path}.props.${transform}`);
  }

  return OK;
}

const NUMERIC_LAYOUT_PROPS = new Set([
  "minWidth", "minHeight", "maxWidth", "maxHeight", "gap", "top", "right", "bottom", "left",
  "grow", "shrink", "opacity", "zIndex", "borderWidth", "borderRadius", "rotate", "scale",
  "scaleX", "scaleY", "skewX", "skewY", "fontSize", "lineHeight", "letterSpacing", "strokeWidth",
]);

const NONNEGATIVE_LAYOUT_PROPS = new Set([
  "minWidth", "minHeight", "maxWidth", "maxHeight", "gap", "borderWidth", "borderRadius",
  "shrink", "grow", "strokeWidth",
]);

function validateLayoutProp(type: string, key: string, value: unknown, path: string, scope: Scope): ValidationResult {
  if (key === "width" || key === "height") {
    if (isSize(value)) return OK;

    const numeric = validateNumeric(value, path, scope);
    if (!numeric.ok) return numeric;
    if (typeof value === "number" && value < 0) return fail("INVALID_PROP_VALUE", path);

    return OK;
  }

  if (key === "padding" || key === "margin") {
    return isSpacing(value) ? OK : fail("INVALID_PROP_TYPE", path);
  }

  if (key === "background" || key === "borderColor" || key === "strokeColor") {
    return validateColor(value, path, scope);
  }

  if (key === "align") {
    if (type === "text") {
      return ["left", "center", "right", "start", "end"].includes(value as string)
        ? OK : fail("INVALID_PROP_VALUE", path);
    }

    return ["start", "center", "end", "stretch"].includes(value as string)
      ? OK : fail("INVALID_PROP_VALUE", path);
  }
  if (key === "justify") {
    return ["start", "center", "end", "space-between"].includes(value as string)
      ? OK : fail("INVALID_PROP_VALUE", path);
  }
  if (key === "fontWeight") {
    return value === "normal" || value === "bold" ? OK : fail("INVALID_PROP_VALUE", path);
  }
  if (key === "textAlign") {
    return value === "start" || value === "center" || value === "end" ? OK : fail("INVALID_PROP_VALUE", path);
  }
  if (key === "shadow" || key === "multiline") {
    return typeof value === "boolean" ? OK : fail("INVALID_PROP_TYPE", path);
  }
  if (key === "size" && type === "item") {
    return Number.isInteger(value) && (value as number) > 0 ? OK : fail("INVALID_PROP_VALUE", path);
  }

  if (!NUMERIC_LAYOUT_PROPS.has(key)) return OK;

  const numeric = validateNumeric(value, path, scope);
  if (!numeric.ok) return numeric;

  if (typeof value === "number" && (key.startsWith("min") || key.startsWith("max")
    || NONNEGATIVE_LAYOUT_PROPS.has(key)) && value < 0) {
    return fail("INVALID_PROP_VALUE", path);
  }

  if (key === "opacity" && typeof value === "number" && (value < 0 || value > 1)) {
    return fail("INVALID_PROP_VALUE", path);
  }
  if (key === "zIndex" && typeof value === "number" && !Number.isSafeInteger(value)) {
    return fail("INVALID_PROP_VALUE", path);
  }
  if ((key === "scale" || key === "scaleX" || key === "scaleY" || key === "fontSize"
    || key === "lineHeight") && typeof value === "number" && value <= 0) {
    return fail("INVALID_PROP_VALUE", path);
  }

  return OK;
}

function validateNumeric(value: unknown, path: string, scope: Scope): ValidationResult {
  if (typeof value === "number") return Number.isFinite(value) ? OK : fail("INVALID_PROP_TYPE", path);

  return validateBinding(value, path, scope, (kind) => ["int", "long", "double"].includes(kind));
}

function isSpacing(value: unknown): boolean {
  if (typeof value === "number") return Number.isFinite(value) && value >= 0;
  if (!isObject(value)) return false;

  return Object.keys(value).every((key) => ["top", "right", "bottom", "left"].includes(key)
    && typeof value[key] === "number" && Number.isFinite(value[key]) && value[key] >= 0);
}

export function hasNonZero(value: unknown): boolean {
  return typeof value === "number" && value !== 0;
}

function isValidPolygon(value: unknown): boolean {
  if (!Array.isArray(value) || value.length < 3 || value.length > 32
    || !value.every((point) => Array.isArray(point) && point.length === 2
      && point.every((coordinate) => Number.isInteger(coordinate)
        && coordinate >= 0 && coordinate <= 10000))) return false;

  const points = value as number[][];
  let area = 0;

  for (let i = 0; i < points.length; i++) {
    const next = points[(i + 1) % points.length];
    if (points[i][0] === next[0] && points[i][1] === next[1]) return false;

    area += points[i][0] * points[(i + 1) % points.length][1]
      - points[(i + 1) % points.length][0] * points[i][1];
  }

  if (area === 0) return false;

  for (let i = 0; i < points.length; i++) {
    if (crossesLaterSegment(points, i)) return false;
  }

  return true;
}

function crossesLaterSegment(points: number[][], i: number): boolean {
  for (let j = i + 1; j < points.length; j++) {
    if (j === i + 1 || (i === 0 && j === points.length - 1)) continue;
    if (segmentsIntersect(
      points[i], points[(i + 1) % points.length], points[j], points[(j + 1) % points.length],
    )) return true;
  }

  return false;
}

function segmentsIntersect(a: number[], b: number[], c: number[], d: number[]): boolean {
  const abC = orientation(a, b, c);
  const abD = orientation(a, b, d);
  const cdA = orientation(c, d, a);
  const cdB = orientation(c, d, b);

  if (abC === 0 && onSegment(a, b, c)) return true;
  if (abD === 0 && onSegment(a, b, d)) return true;
  if (cdA === 0 && onSegment(c, d, a)) return true;
  if (cdB === 0 && onSegment(c, d, b)) return true;

  return (abC > 0) !== (abD > 0) && (cdA > 0) !== (cdB > 0);
}

function orientation(a: number[], b: number[], c: number[]): number {
  return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
}

function onSegment(a: number[], b: number[], point: number[]): boolean {
  return point[0] >= Math.min(a[0], b[0]) && point[0] <= Math.max(a[0], b[0])
    && point[1] >= Math.min(a[1], b[1]) && point[1] <= Math.max(a[1], b[1]);
}
