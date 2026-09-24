import { describe, expect, it } from "bun:test";
import * as crypto from "crypto";
import * as fs from "fs";
import * as os from "os";
import * as path from "path";
import * as zlib from "zlib";

import { validateContract } from "@rain-ui/core";

const projectRoot = path.join(import.meta.dir, "../../..");
const cliPath = path.join(projectRoot, "packages/cli/index.ts");
const shopDir = path.join(projectRoot, "examples/shop");
const fixturesDir = path.join(import.meta.dir, "../test/fixtures");

interface RunResult {
  status: number | null;
  stdout: string;
  stderr: string;
}

function runRain(args: string[], cwd: string = projectRoot): RunResult {
  const result = Bun.spawnSync(["bun", cliPath, ...args], { cwd });

  return {
    status: result.exitCode,
    stdout: result.stdout.toString(),
    stderr: result.stderr.toString(),
  };
}

function tempDir(): string {
  return fs.mkdtempSync(path.join(os.tmpdir(), "rain-cli-"));
}

function fixture(name: string): string {
  return path.join(fixturesDir, name);
}

function build(dir: string, ...extra: string[]): { out: string; result: RunResult } {
  const out = tempDir();
  const result = runRain(["build", dir, "--out", out, ...extra]);

  return { out, result };
}

function readManifest(out: string): any {
  return JSON.parse(fs.readFileSync(path.join(out, "manifest.json"), "utf-8"));
}

function sha256(bytes: Buffer): string {
  return crypto.createHash("sha256").update(bytes).digest("hex");
}

function sortKeys(value: unknown): unknown {
  if (value === null || typeof value !== "object") return value;
  if (Array.isArray(value)) return value.map(sortKeys);

  const sorted: Record<string, unknown> = {};
  for (const key of Object.keys(value).sort()) {
    sorted[key] = sortKeys((value as Record<string, unknown>)[key]);
  }

  return sorted;
}

describe("rain build", () => {
  it("writes the contract and a manifest with its sha256", () => {
    const { out, result } = build(shopDir);

    expect(result.status).toBe(0);

    const contractPath = path.join(out, "shop/main.json");
    expect(fs.existsSync(contractPath)).toBe(true);

    const manifest = readManifest(out);
    expect(manifest).toEqual({
      schemaVersion: 0,
      screens: {
        "shop:main": {
          file: "shop/main.json",
          sha256: sha256(fs.readFileSync(contractPath)),
        },
      },
    });
  });

  it("writes canonical JSON with sorted keys and no whitespace", () => {
    const { out, result } = build(shopDir);

    expect(result.status).toBe(0);

    const text = fs.readFileSync(path.join(out, "shop/main.json"), "utf-8");
    expect(text).toBe(JSON.stringify(sortKeys(JSON.parse(text))));
  });

  it("writes a contract that passes validation", () => {
    const { out, result } = build(shopDir);

    expect(result.status).toBe(0);

    const text = fs.readFileSync(path.join(out, "shop/main.json"), "utf-8");
    const validation = validateContract(JSON.parse(text), { sourceBytes: Buffer.byteLength(text, "utf-8") });
    expect(validation).toEqual({ ok: true });
  });

  it("writes to <cwd>/dist when --out is not given", () => {
    const cwd = tempDir();
    const result = runRain(["build", shopDir], cwd);

    expect(result.status).toBe(0);
    expect(fs.existsSync(path.join(cwd, "dist/shop/main.json"))).toBe(true);
    expect(fs.existsSync(path.join(cwd, "dist/manifest.json"))).toBe(true);
  });

  it("produces the same bytes for a split-file screen and its single-file equivalent", () => {
    const split = build(shopDir);
    const inline = build(fixture("inline-shop"));

    expect(split.result.status).toBe(0);
    expect(inline.result.status).toBe(0);

    const splitBytes = fs.readFileSync(path.join(split.out, "shop/main.json"));
    const inlineBytes = fs.readFileSync(path.join(inline.out, "shop/main.json"));
    expect(inlineBytes.equals(splitBytes)).toBe(true);
    expect(readManifest(inline.out)).toEqual(readManifest(split.out));
  });

  it("produces the same manifest on two builds of the same screens", () => {
    const first = build(shopDir);
    const second = build(shopDir);

    expect(first.result.status).toBe(0);
    expect(second.result.status).toBe(0);
    expect(readManifest(second.out)).toEqual(readManifest(first.out));
  });
});

describe("rain build screen discovery", () => {
  it("only builds modules whose default export comes from defineScreen", () => {
    const { out, result } = build(fixture("discovery"));

    expect(result.status).toBe(0);
    expect(Object.keys(readManifest(out).screens)).toEqual(["test:discovery"]);
  });

  it("fails when two screens share the same id", () => {
    const { result } = build(fixture("duplicate-id"));

    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain("test:dup");
    expect(result.stderr.toLowerCase()).toContain("duplicate");
  });

  it("fails with the error code and node path when a screen is invalid", () => {
    const { result } = build(fixture("invalid-screen"));

    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain("UNDECLARED_ACTION");
    expect(result.stderr).toContain("root.props.action");
  });
});

describe("rain build determinism warnings", () => {
  it("warns about Math.random(), Date.now() and new Date() in imported local modules", () => {
    const { result } = build(fixture("determinism-warnings"));

    expect(result.status).toBe(0);

    const lines = result.stderr.split("\n");
    const warningFor = (call: string, file: string) =>
      lines.some((line) => line.includes(call) && line.includes(file));

    expect(warningFor("Math.random()", path.join("components", "Random.tsx"))).toBe(true);
    expect(warningFor("Date.now()", path.join("helpers", "time.ts"))).toBe(true);
    expect(warningFor("new Date()", path.join("helpers", "time.ts"))).toBe(true);
  });

  it("does not warn about calls inside node_modules", () => {
    const { out, result } = build(fixture("node-modules-warning"));

    expect(result.status).toBe(0);
    expect(Object.keys(readManifest(out).screens)).toEqual(["test:node-modules"]);
    expect(result.stderr).not.toContain("Math.random()");
  });
});

describe("rain build --check", () => {
  it("passes on a deterministic screen", () => {
    const { result } = build(shopDir, "--check");

    expect(result.status).toBe(0);
  });

  it("fails when two builds produce different hashes", () => {
    const { result } = build(fixture("nondeterministic"), "--check");

    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain("test:nondeterministic");
    expect(result.stderr.toLowerCase()).toContain("hash");
  });
});

describe("rain build unused property warnings", () => {
  it("warns about declared properties the screen never binds, reporting only the outermost unused path", () => {
    const { result } = build(fixture("unused-properties"));

    expect(result.status).toBe(0);

    const warnings = result.stderr.split("\n").filter((line) => line.includes("unused property"));
    expect(warnings).toHaveLength(2);
    expect(warnings.some((line) => line.includes("listings[].sellerId") && line.includes("test:unused"))).toBe(true);
    expect(warnings.some((line) => line.includes("listings[].bids") && line.includes("test:unused"))).toBe(true);
  });

  it("counts a property used only in a payload as used", () => {
    const { result } = build(fixture("unused-properties"));

    expect(result.stderr).not.toContain("listings[].id");
  });
});

describe("rain build examples", () => {
  for (const example of ["gts", "city", "land"]) {
    it(`builds examples/${example} into valid contracts`, () => {
      const { out, result } = build(path.join(projectRoot, "examples", example));

      expect(result.status).toBe(0);

      const screens = Object.values(readManifest(out).screens) as { file: string }[];
      expect(screens.length).toBeGreaterThan(0);

      for (const { file } of screens) {
        const text = fs.readFileSync(path.join(out, file), "utf-8");
        const validation = validateContract(JSON.parse(text), { sourceBytes: Buffer.byteLength(text, "utf-8") });
        expect(validation).toEqual({ ok: true });
      }
    });
  }
});

function assetFile(out: string, hash: string): string {
  return path.join(out, "assets", hash);
}

function readContract(out: string, file: string): any {
  return JSON.parse(fs.readFileSync(path.join(out, file), "utf-8"));
}

function pngChunk(type: string, data: Buffer): Buffer {
  const length = Buffer.alloc(4);
  const crc = Buffer.alloc(4);
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);

  length.writeUInt32BE(data.length);
  crc.writeUInt32BE(zlib.crc32(body));

  return Buffer.concat([length, body, crc]);
}

// A valid 1x1 PNG padded with a tEXt chunk, so the file is over the limit while its header is fine.
function oversizedPng(bytes: number): Buffer {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(1, 0);
  header.writeUInt32BE(1, 4);
  header.writeUInt8(8, 8);
  header.writeUInt8(6, 9);

  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    pngChunk("IHDR", header),
    pngChunk("tEXt", Buffer.concat([Buffer.from("Comment\0", "latin1"), Buffer.alloc(bytes, "x")])),
    pngChunk("IDAT", zlib.deflateSync(Buffer.from([0, 0, 0, 0, 0]))),
    pngChunk("IEND", Buffer.alloc(0)),
  ]);
}

describe("rain build assets", () => {
  const basicDir = fixture("assets-basic");
  const banner = fs.readFileSync(path.join(basicDir, "images/banner.png"));
  const spinner = fs.readFileSync(path.join(basicDir, "images/spinner.gif"));
  const title = fs.readFileSync(path.join(basicDir, "fonts/title.ttf"));

  it("copies each imported asset to dist/assets/<sha256> with the original bytes and lists it in the manifest", () => {
    const { out, result } = build(basicDir);

    expect(result.status).toBe(0);

    const hashes = [banner, spinner, title].map(sha256);
    for (const [index, bytes] of [banner, spinner, title].entries()) {
      expect(fs.readFileSync(assetFile(out, hashes[index])).equals(bytes)).toBe(true);
    }

    expect(fs.readdirSync(path.join(out, "assets")).sort()).toEqual([...hashes].sort());
    expect(readManifest(out).assets).toEqual([...hashes].sort());
  });

  it("writes the asset table and the $asset references into the contract", () => {
    const { out, result } = build(basicDir);

    expect(result.status).toBe(0);

    const file = readManifest(out).screens["test:assets"].file;
    const contract = readContract(out, file);
    expect(contract.assets[sha256(banner)]).toEqual({ type: "image/png", bytes: banner.length, width: 32, height: 16 });
    expect(contract.assets[sha256(title)]).toEqual({ type: "font/ttf", bytes: title.length });
    expect(contract.root.children[0].props.src).toEqual({ $asset: sha256(banner) });
    expect(contract.root.children[2].props.font).toEqual({ $asset: sha256(title) });

    const text = fs.readFileSync(path.join(out, file), "utf-8");
    const validation = validateContract(JSON.parse(text), { sourceBytes: Buffer.byteLength(text, "utf-8") });
    expect(validation).toEqual({ ok: true });
  });

  it("records the frame count of an animated GIF", () => {
    const { out, result } = build(basicDir);

    expect(result.status).toBe(0);

    const contract = readContract(out, readManifest(out).screens["test:assets"].file);
    expect(contract.assets[sha256(spinner)]).toEqual({
      type: "image/gif",
      bytes: spinner.length,
      width: 16,
      height: 16,
      frames: 4,
    });
  });

  it("writes one asset for the same content used by two screens", () => {
    const { out, result } = build(fixture("assets-shared"));

    expect(result.status).toBe(0);

    const hash = sha256(fs.readFileSync(path.join(fixture("assets-shared"), "banner.png")));
    const manifest = readManifest(out);
    expect(manifest.assets).toEqual([hash]);
    expect(fs.readdirSync(path.join(out, "assets"))).toEqual([hash]);

    for (const id of ["test:shared-a", "test:shared-b"]) {
      expect(Object.keys(readContract(out, manifest.screens[id].file).assets)).toEqual([hash]);
    }
  });

  it("fails with LIMIT_EXCEEDED and the file path when an image is wider than 4096 px", () => {
    const { result } = build(fixture("assets-too-wide"));

    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain("LIMIT_EXCEEDED");
    expect(result.stderr).toContain(path.join("images", "wide.png"));
  });

  it("fails with UNSUPPORTED_ASSET and the file path when an asset is corrupted", () => {
    const { result } = build(fixture("assets-corrupt"));

    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain("UNSUPPORTED_ASSET");
    expect(result.stderr).toContain(path.join("images", "broken.png"));
  });

  it("fails with UNSUPPORTED_ASSET and the file path for an unsupported extension", () => {
    const { result } = build(fixture("assets-unsupported"));

    expect(result.status).not.toBe(0);
    expect(result.stderr).toContain("UNSUPPORTED_ASSET");
    expect(result.stderr).toContain(path.join("images", "photo.webp"));
  });

  it("fails with LIMIT_EXCEEDED and the file path when an asset is over 8 MiB", () => {
    const dir = tempDir();
    const screen = `import { defineActions, defineProperties, defineScreen } from "@rain-ui/core";

import huge from "./huge.png";

export default defineScreen({
  id: "test:huge",
  properties: defineProperties({}),
  actions: defineActions({}),
  render: () => <image src={huge} />,
});
`;

    // The screen lives outside the repository, so @rain-ui/core is reached through a link to the root node_modules.
    fs.symlinkSync(path.join(projectRoot, "node_modules"), path.join(dir, "node_modules"), "dir");
    fs.writeFileSync(path.join(dir, "screen.tsx"), screen);
    fs.writeFileSync(path.join(dir, "huge.png"), oversizedPng(8 * 1024 * 1024));

    try {
      expect(fs.statSync(path.join(dir, "huge.png")).size).toBeGreaterThan(8 * 1024 * 1024);

      const { result } = build(dir);

      expect(result.status).not.toBe(0);
      expect(result.stderr).toContain("LIMIT_EXCEEDED");
      expect(result.stderr).toContain("huge.png");
    } finally {
      fs.rmSync(dir, { recursive: true, force: true });
    }
  });
});
