import { describe, expect, it } from "bun:test";

import { t } from "./builder";

function serialized(schema: unknown): unknown {
  return JSON.parse(JSON.stringify(schema));
}

describe("t builder", () => {
  it("serializes plain types with only their kind", () => {
    expect(Object.keys(t.string())).toEqual(["kind"]);
    expect(serialized(t.double())).toEqual({ kind: "double" });
  });

  it("serializes .optional() as an optional flag", () => {
    expect(serialized(t.string().optional())).toEqual({ kind: "string", optional: true });
    expect(serialized(t.list(t.item()).optional())).toEqual({ kind: "list", of: { kind: "item" }, optional: true });
    expect(serialized(t.object({ name: t.string() }).optional())).toEqual({
      kind: "object",
      fields: { name: { kind: "string" } },
      optional: true,
    });
  });

  it("serializes .default() as the default value, without an optional flag", () => {
    expect(serialized(t.string().default("Sem descrição"))).toEqual({ kind: "string", default: "Sem descrição" });
    expect(serialized(t.int().default(0))).toEqual({ kind: "int", default: 0 });
    expect(serialized(t.long().default(10))).toEqual({ kind: "long", default: 10 });
    expect(serialized(t.bool().default(false))).toEqual({ kind: "bool", default: false });
    expect(serialized(t.double().default(1.5))).toEqual({ kind: "double", default: 1.5 });
  });

  it("keeps the modifier methods out of the serialized schema", () => {
    const schema = t.object({ subtitle: t.string().optional(), count: t.int().default(0) });

    expect(JSON.stringify(schema)).toBe(
      '{"kind":"object","fields":{"subtitle":{"kind":"string","optional":true},"count":{"kind":"int","default":0}}}',
    );
  });

  it("only offers .default() on scalar types", () => {
    expect("default" in t.item()).toBe(false);
    expect("default" in t.list(t.string())).toBe(false);
    expect("default" in t.object({})).toBe(false);
  });

  it("does not change a schema when a modifier is applied to it", () => {
    const base = t.string();
    base.optional();
    base.default("x");

    expect(serialized(base)).toEqual({ kind: "string" });
  });
});

describe("t.asset", () => {
  it("serializes as the asset kind", () => {
    expect(serialized(t.asset())).toEqual({ kind: "asset" });
  });

  it("can be optional", () => {
    expect(serialized(t.asset().optional())).toEqual({ kind: "asset", optional: true });
  });

  it("does not offer .default()", () => {
    expect("default" in t.asset()).toBe(false);
  });
});

// Checked by the type checker, not at runtime: a default must have the type of its schema.
function typeChecks() {
  // @ts-expect-error a string schema does not accept a number default
  t.string().default(5);
  // @ts-expect-error an int schema does not accept a string default
  t.int().default("5");
  // @ts-expect-error a bool schema does not accept a string default
  t.bool().default("true");
}
void typeChecks;
