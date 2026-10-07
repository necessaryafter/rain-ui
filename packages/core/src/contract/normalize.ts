const COLOR_PROPS = new Set(["background", "borderColor", "strokeColor", "color"]);
const SPACING_PROPS = new Set(["padding", "margin"]);
const SHORT_COLOR = /^#([0-9A-Fa-f]{3})$/;
const LONG_COLOR = /^#[0-9A-Fa-f]{6}$/;

export function normalizeProps(type: string, props: Record<string, unknown>): Record<string, unknown> {
  const normalized: Record<string, unknown> = {};

  for (const [key, value] of Object.entries(props)) {
    if (type === "text" && key === "align") continue;

    normalized[key] = normalizeProp(key, value);
  }

  if (type === "text" && props.textAlign === undefined && props.align !== undefined) {
    normalized.textAlign = normalizeTextAlign(props.align);
  }

  return normalized;
}

function normalizeProp(key: string, value: unknown): unknown {
  if (COLOR_PROPS.has(key) && typeof value === "string") {
    const short = SHORT_COLOR.exec(value);
    if (short) return `#${[...short[1]].map((digit) => digit.repeat(2)).join("")}`.toUpperCase();
    if (LONG_COLOR.test(value)) return value.toUpperCase();
  }

  if (SPACING_PROPS.has(key) && isSpacingObject(value)) {
    return { top: 0, right: 0, bottom: 0, left: 0, ...value };
  }

  return value;
}

function isSpacingObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value) && !("$bind" in value);
}

function normalizeTextAlign(value: unknown): unknown {
  if (value === "left") return "start";
  if (value === "right") return "end";

  return value;
}
