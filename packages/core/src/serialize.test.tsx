import { describe, expect, it } from "bun:test";

import { defineActions, defineProperties, defineScreen, t } from "./builder";
import { compileScreen } from "./serialize";

const properties = defineProperties({
  name: t.string(),
  subtitle: t.string().optional(),
  price: t.int(),
  status: t.string(),
  owner: t.object({ name: t.string() }),
  items: t.list(t.object({
    name: t.string(),
    bidData: t.object({ bidderName: t.string() }),
  })),
});

function compile(render: (p: any) => any) {
  return compileScreen(defineScreen({
    id: "test:screen",
    properties,
    actions: defineActions({}),
    render,
  }));
}

describe("compileScreen conditions", () => {
  it("compiles <show> with the fallback prop into a trailing fallback node", () => {
    const { contract } = compile((p) => (
      <show when={p.subtitle} fallback={<text value="Sem descrição" />}>
        <text value={p.subtitle} />
      </show>
    ));

    expect(contract.root).toEqual({
      type: "show",
      props: { when: { $bind: "subtitle" } },
      children: [
        { type: "text", props: { value: { $bind: "subtitle" } }, children: [] },
        {
          type: "fallback",
          props: {},
          children: [{ type: "text", props: { value: "Sem descrição" }, children: [] }],
        },
      ],
    });
  });

  it("compiles <show> without a fallback into only its children", () => {
    const { contract } = compile((p) => (
      <show when={p.subtitle}>
        <text value={p.subtitle} />
      </show>
    ));

    expect(contract.root.children).toEqual([
      { type: "text", props: { value: { $bind: "subtitle" } }, children: [] },
    ]);
  });

  it("compiles <match> with its cases and default", () => {
    const { contract } = compile((p) => (
      <match value={p.status}>
        <case is="ACTIVE"><text value="Ativo" /></case>
        <default><text value="Outro" /></default>
      </match>
    ));

    expect(contract.root).toEqual({
      type: "match",
      props: { value: { $bind: "status" } },
      children: [
        { type: "case", props: { is: "ACTIVE" }, children: [{ type: "text", props: { value: "Ativo" }, children: [] }] },
        { type: "default", props: {}, children: [{ type: "text", props: { value: "Outro" }, children: [] }] },
      ],
    });
  });

  it("serializes an object property used as a binding to its path", () => {
    const { contract } = compile((p) => (
      <column>
        <show when={p.owner}><text value={p.owner.name} /></show>
        <list source={p.items}>
          {(item: any) => (
            <show when={item.bidData}><text value={item.bidData.bidderName} /></show>
          )}
        </list>
      </column>
    ));

    const [ownerShow, list] = contract.root.children;
    expect(ownerShow.props.when).toEqual({ $bind: "owner" });
    expect(list.children[0].props.when).toEqual({ $bind: "bidData" });
    expect(list.children[0].children[0].props.value).toEqual({ $bind: "bidData.bidderName" });
  });
});

describe("compileScreen rejects bindings used as JS values", () => {
  it("rejects a binding inside a template string", () => {
    expect(() => compile((p) => <text value={`Olá ${p.name}`} />)).toThrow(/binding/);
  });

  it("rejects string concatenation with a binding", () => {
    expect(() => compile((p) => <text value={p.name + "!"} />)).toThrow(/binding/);
  });

  it("rejects comparing a binding", () => {
    expect(() => compile((p) => <button action="test:x" disabled={p.price > 3} />)).toThrow(/binding/);
  });

  it("points to <list> when reading .length of a list binding", () => {
    expect(() => compile((p) => <text value={String(p.items.length)} />)).toThrow(/<list>/);
  });

  it("points to <list> when mapping over a list binding", () => {
    expect(() => compile((p) => <column>{p.items.map(() => <text value="x" />)}</column>)).toThrow(/<list>/);
  });

  it("rejects a field that is not declared on an object", () => {
    expect(() => compile((p) => <text value={p.owner.missing} />)).toThrow(/Unknown property/);
  });
});
