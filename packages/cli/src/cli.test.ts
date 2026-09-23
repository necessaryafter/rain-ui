import { describe, expect, it } from "bun:test";
import * as crypto from "crypto";
import * as fs from "fs";
import * as os from "os";
import * as path from "path";

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
