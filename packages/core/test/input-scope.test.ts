import { expect, test } from "bun:test";

import { validateContract } from "../src/validate";

function contract(screenInput: boolean) {
  const list = (source: string, child: unknown) => ({
    type: "list",
    props: { source: { $bind: source } },
    children: [{ type: "box", key: { $bind: "id" }, props: {}, children: [child] }],
  });

  return {
    schemaVersion: 0,
    id: "test:input-scope",
    properties: {
      first: { kind: "list", of: { kind: "object", fields: { id: { kind: "string" } } } },
      second: { kind: "list", of: { kind: "object", fields: { id: { kind: "string" } } } },
    },
    actions: { "test:send": { kind: "object", fields: { query: { kind: "string" } } } },
    root: {
      type: "box",
      props: {},
      children: [
        list("first", {
          type: "button",
          props: { action: "test:send", payload: { query: { $input: "query" } } },
          children: [],
        }),
        list("second", { type: "input", props: { id: "query" }, children: [] }),
        ...(screenInput ? [{ type: "input", props: { id: "query" }, children: [] }] : []),
      ],
    },
  };
}

test("input references cannot read an input from another keyed list", () => {
  expect(validateContract(contract(false))).toEqual({
    ok: false,
    error: { code: "UNDECLARED_INPUT", path: "root.children[0].children[0].children[0].props.payload.query" },
  });
});

test("a screen-scoped input remains readable from a keyed list", () => {
  expect(validateContract(contract(true))).toEqual({ ok: true });
});
