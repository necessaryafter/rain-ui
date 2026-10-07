import { expect, test } from "bun:test";

import { defineActions, defineProperties, defineScreen } from "../src/authoring/builder";
import { polygon } from "../src/authoring/polygon";
import { compileScreen } from "../src/contract/serialize";
import { validateContract } from "../src/validate";

function compilePolygon() {
  return compileScreen(defineScreen({
    id: "test:polygon",
    properties: defineProperties({}),
    actions: defineActions({}),
    render: () => (
      <box shape={polygon("0% 15%", "100% 0%", "97% 80%", "3% 100%")} />
    ),
  }));
}

test("polygon serializes coordinate pairs that both contract validators accept", () => {
  const { contract } = compilePolygon();

  expect(contract.root.props.shape).toEqual([[0, 1500], [10000, 0], [9700, 8000], [300, 10000]]);
  expect(validateContract(contract)).toEqual({ ok: true });
});

test("polygon validation rejects a nonzero-area self-intersection and a zero-length edge", () => {
  const { contract } = compilePolygon();

  contract.root.props.shape = [[0, 0], [10000, 0], [10000, 10000], [0, 5000], [10000, 5000]];
  expect(validateContract(contract)).toEqual({
    ok: false,
    error: { code: "INVALID_POLYGON", path: "root.props.shape" },
  });

  contract.root.props.shape = [[0, 0], [10000, 0], [10000, 0], [0, 10000]];
  expect(validateContract(contract)).toEqual({
    ok: false,
    error: { code: "INVALID_POLYGON", path: "root.props.shape" },
  });
});
