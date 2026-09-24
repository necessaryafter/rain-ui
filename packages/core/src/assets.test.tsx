import { describe, expect, it } from "bun:test";

import shop from "../../../examples/shop/main";
import { defineActions, defineProperties, defineScreen, t } from "./builder";
import { compileScreen } from "./serialize";
import { validateContract } from "./validate";

const BANNER = "3f0a1c8e2b7d4f6a9c1e5b8d2a4f7c9e1b3d5f7a9c2e4b6d8f0a2c4e6b8d0f2a";
const TITLE_FONT = "7c4e2a9b1d3f5e8a0c2b4d6f8e1a3c5b7d9f2e4a6c8b0d1f3e5a7c9b2d4f6e8a";
const FIRE = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
const WATER = "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0";

// What `import banner from "./banner.png"` evaluates to once rain build has hashed and read the file: the reference
// the contract uses, plus the entry that goes into the contract's asset table.
const banner = { $asset: BANNER, type: "image/png", bytes: 48213, width: 128, height: 64 };
const titleFont = { $asset: TITLE_FONT, type: "font/ttf", bytes: 21504 };
const fire = { $asset: FIRE, type: "image/png", bytes: 512, width: 16, height: 16 };
const water = { $asset: WATER, type: "image/png", bytes: 498, width: 16, height: 16 };

function isValid(json: string): boolean {
  return validateContract(JSON.parse(json), { sourceBytes: Buffer.byteLength(json, "utf-8") }).ok;
}

describe("compileScreen with imported assets", () => {
  it("replaces an imported asset with a $asset reference and registers it in the asset table", () => {
    const { contract, json } = compileScreen(defineScreen({
      id: "test:assets",
      properties: defineProperties({}),
      actions: defineActions({}),
      render: () => (
        <column>
          <image src={banner} width={64} height={32} />
          <text value="GTS" font={titleFont} />
        </column>
      ),
    }));

    expect(contract.root.children).toEqual([
      { type: "image", props: { src: { $asset: BANNER }, width: 64, height: 32 }, children: [] },
      { type: "text", props: { value: "GTS", font: { $asset: TITLE_FONT } }, children: [] },
    ]);
    expect(JSON.parse(json).assets).toEqual({
      [BANNER]: { type: "image/png", bytes: 48213, width: 128, height: 64 },
      [TITLE_FONT]: { type: "font/ttf", bytes: 21504 },
    });
    expect(isValid(json)).toBe(true);
  });

  it("registers an asset used more than once a single time", () => {
    const { json } = compileScreen(defineScreen({
      id: "test:assets",
      properties: defineProperties({}),
      actions: defineActions({}),
      render: () => (
        <row>
          <image src={banner} />
          <image src={banner} />
        </row>
      ),
    }));

    expect(Object.keys(JSON.parse(json).assets)).toEqual([BANNER]);
  });
});

describe("compileScreen with named assets", () => {
  it("turns defineScreen({ assets }) into assetNames and registers each named asset", () => {
    const { contract, json } = compileScreen(defineScreen({
      id: "test:types",
      properties: defineProperties({ icon: t.asset() }),
      actions: defineActions({}),
      assets: { fire, water },
      render: (p) => <image src={p.icon} width={16} height={16} />,
    }));

    const parsed = JSON.parse(json);
    expect(parsed.assetNames).toEqual({ fire: FIRE, water: WATER });
    expect(parsed.assets).toEqual({
      [FIRE]: { type: "image/png", bytes: 512, width: 16, height: 16 },
      [WATER]: { type: "image/png", bytes: 498, width: 16, height: 16 },
    });
    expect(parsed.properties.icon).toEqual({ kind: "asset" });
    expect(contract.root.props.src).toEqual({ $bind: "icon" });
    expect(isValid(json)).toBe(true);
  });
});

describe("compileScreen without assets", () => {
  it("keeps the examples/shop contract hash unchanged", () => {
    const { json, sha256 } = compileScreen(shop);

    expect(JSON.parse(json)).not.toHaveProperty("assets");
    expect(JSON.parse(json)).not.toHaveProperty("assetNames");
    expect(sha256).toBe("ab38c5d70aaddd5a86f2cc907cd893a2fa76895a1e8b1363ad3635add776bbef");
  });
});
