import { expect, test } from "bun:test";

import { defineActions, defineProperties, defineScreen, t } from "../src/authoring/builder";
import { compileScreen } from "../src/contract/serialize";
import { validateContract } from "../src/validate";

function contract(type: string, props: Record<string, unknown>, children: unknown[] = []) {
  return {
    schemaVersion: 0,
    id: "test:visual-props",
    properties: { width: { kind: "int" }, color: { kind: "string" }, wrong: { kind: "bool" } },
    actions: {},
    root: { type, props, children },
  };
}

function error(type: string, props: Record<string, unknown>, children: unknown[] = []) {
  const result = validateContract(contract(type, props, children));
  if (result.ok) throw new Error("expected validation failure");

  return result.error;
}

test("build normalizes short colors and per-side spacing without changing bindings", () => {
  const screen = defineScreen({
    id: "test:normalized",
    properties: defineProperties({ color: t.string() }),
    actions: defineActions({}),
    render: (p) => (
      <box background="#aBc" padding={{ top: 2, left: 4 }} borderColor={p.color}>
        <text value="Old alignment" color="#f00" align="left" />
      </box>
    ),
  });
  const { contract: compiled, json } = compileScreen(screen);

  expect(compiled.root.props).toEqual({
    background: "#AABBCC",
    padding: { top: 2, right: 0, bottom: 0, left: 4 },
    borderColor: { $bind: "color" },
  });
  expect(compiled.root.children[0].props).toEqual({
    value: "Old alignment",
    color: "#FF0000",
    textAlign: "start",
  });
  expect(validateContract(JSON.parse(json))).toEqual({ ok: true });
});

test("visual bindings distinguish undeclared and wrongly typed properties", () => {
  expect(error("box", { width: { $bind: "missing" } })).toEqual({
    code: "UNDECLARED_BINDING", path: "root.props.width",
  });
  expect(error("box", { width: { $bind: "wrong" } })).toEqual({
    code: "BINDING_TYPE_MISMATCH", path: "root.props.width",
  });
  expect(error("box", { background: { $bind: "wrong" } })).toEqual({
    code: "BINDING_TYPE_MISMATCH", path: "root.props.background",
  });
  expect(validateContract(contract("box", { width: { $bind: "width" }, background: { $bind: "color" } })))
    .toEqual({ ok: true });
});

test("visual literals reject invalid bounds, anchors, and text styling", () => {
  expect(error("box", { width: -1 })).toEqual({ code: "INVALID_PROP_VALUE", path: "root.props.width" });
  expect(error("box", { opacity: 1.5 })).toEqual({ code: "INVALID_PROP_VALUE", path: "root.props.opacity" });
  expect(error("box", { left: 1, right: 2 })).toEqual({
    code: "INVALID_PROP_COMBINATION", path: "root.props.right",
  });
  expect(error("box", { margin: { top: 1, diagonal: 2 } })).toEqual({
    code: "INVALID_PROP_TYPE", path: "root.props.margin",
  });
  expect(error("text", { value: "a", fontWeight: "heavy" })).toEqual({
    code: "INVALID_PROP_VALUE", path: "root.props.fontWeight",
  });
  expect(error("text", { value: "a", strokeWidth: -1 })).toEqual({
    code: "INVALID_PROP_VALUE", path: "root.props.strokeWidth",
  });
});

test("a transformed ancestor cannot contain a clipped descendant", () => {
  const scroll = { type: "scroll", props: { width: 50, height: 50 }, children: [] };

  expect(error("box", { rotate: 10 }, [scroll])).toEqual({
    code: "UNSUPPORTED_COMBINATION", path: "root.props.rotate",
  });
  expect(error("box", { skewX: { $bind: "width" } }, [scroll])).toEqual({
    code: "UNSUPPORTED_COMBINATION", path: "root.props.skewX",
  });
});

test("grid dimensions accept declared integer bindings and reject other kinds", () => {
  expect(validateContract(contract("grid", {
    cellWidth: { $bind: "width" }, cellHeight: 20, width: { $bind: "width" },
  }))).toEqual({ ok: true });
  expect(error("grid", {
    cellWidth: { $bind: "wrong" }, cellHeight: 20, width: 100,
  })).toEqual({ code: "BINDING_TYPE_MISMATCH", path: "root.props.cellWidth" });
});

test("inputs and tabs require usable IDs and a selected tab", () => {
  expect(error("input", {})).toEqual({ code: "INVALID_PROP_TYPE", path: "root.props.id" });
  expect(error("tab", { id: "a", label: "A" })).toEqual({
    code: "MISPLACED_COMPONENT", path: "root",
  });
  expect(error("tabs", { defaultTab: "missing" }, [
    { type: "tab", props: { id: "a", label: "A" }, children: [] },
  ])).toEqual({ code: "INVALID_PROP_VALUE", path: "root.props.defaultTab" });
});
