import * as fs from "fs";
import * as path from "path";

import { compileScreen, isScreenDefinition, validateContract, type CompiledScreen } from "@rain-ui/core";

import { assetFiles } from "./assets";

const MODULE_EXTENSIONS = new Set([".ts", ".tsx", ".js", ".jsx"]);

export interface BuiltScreen {
  source: string;
  compiled: CompiledScreen;
}

export interface BuildResult {
  screens: BuiltScreen[];
  errors: string[];
}

export interface Manifest {
  schemaVersion: 0;
  screens: Record<string, { file: string; sha256: string }>;
  assets?: string[];
}

export async function buildScreens(dir: string): Promise<BuildResult> {
  const screens: BuiltScreen[] = [];
  const errors: string[] = [];

  for (const source of findModules(dir)) {
    const screen = await buildModule(source, path.relative(dir, source), errors);
    if (screen) screens.push(screen);
  }

  errors.push(...findDuplicateIds(screens, dir));

  return { screens, errors };
}

export function writeOutput(out: string, screens: BuiltScreen[]): void {
  for (const { compiled } of screens) {
    const file = path.join(out, outputFile(compiled.id));

    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, compiled.json);
  }

  const hashes = assetHashes(screens);
  if (hashes.length > 0) {
    fs.mkdirSync(path.join(out, "assets"), { recursive: true });
  }

  for (const hash of hashes) {
    fs.copyFileSync(assetFiles.get(hash)!, path.join(out, "assets", hash));
  }

  fs.writeFileSync(path.join(out, "manifest.json"), JSON.stringify(createManifest(screens), null, 2) + "\n");
}

export function createManifest(screens: BuiltScreen[]): Manifest {
  const manifest: Manifest = {
    schemaVersion: 0,
    screens: {},
  };

  const sorted = [...screens].sort((a, b) => a.compiled.id.localeCompare(b.compiled.id));
  for (const { compiled } of sorted) {
    manifest.screens[compiled.id] = {
      file: outputFile(compiled.id),
      sha256: compiled.sha256,
    };
  }

  // Absent rather than empty, so builds without assets keep the manifest shape they had before assets existed.
  const hashes = assetHashes(screens);
  if (hashes.length > 0) {
    manifest.assets = hashes;
  }

  return manifest;
}

// Every asset the screens use, once per hash even when several screens or files share the same content.
function assetHashes(screens: BuiltScreen[]): string[] {
  const hashes = new Set<string>();
  for (const { compiled } of screens) {
    for (const hash of Object.keys(compiled.contract.assets ?? {})) {
      hashes.add(hash);
    }
  }

  return [...hashes].sort();
}

// A valid id only has [a-z0-9_/-] after the colon, so the path can never climb out of the output directory.
function outputFile(id: string): string {
  const [namespace, screen] = id.split(":");

  return `${namespace}/${screen}.json`;
}

async function buildModule(source: string, label: string, errors: string[]): Promise<BuiltScreen | undefined> {
  let module: { default?: unknown };

  try {
    module = await import(source);
  } catch (error) {
    errors.push(`${label}: failed to load: ${describe(error)}`);
    return undefined;
  }

  if (!isScreenDefinition(module.default)) return undefined;

  let compiled: CompiledScreen;

  try {
    compiled = compileScreen(module.default);
  } catch (error) {
    errors.push(`${label}: failed to render ${module.default.id}: ${describe(error)}`);
    return undefined;
  }

  const validation = validateContract(JSON.parse(compiled.json), {
    sourceBytes: Buffer.byteLength(compiled.json, "utf-8"),
  });
  if (!validation.ok) {
    errors.push(`${label}: ${compiled.id}: ${validation.error.code} at ${validation.error.path}`);
    return undefined;
  }

  // An AssetRef written by hand instead of imported has no file behind it to publish.
  const missing = Object.keys(compiled.contract.assets ?? {}).filter((hash) => !assetFiles.has(hash));
  if (missing.length > 0) {
    errors.push(`${label}: ${compiled.id}: UNDECLARED_ASSET: ${missing.join(", ")} was not imported from a file`);
    return undefined;
  }

  return { source, compiled };
}

function findDuplicateIds(screens: BuiltScreen[], dir: string): string[] {
  const sourcesById = new Map<string, string[]>();

  for (const { source, compiled } of screens) {
    const sources = sourcesById.get(compiled.id) ?? [];

    sources.push(path.relative(dir, source));
    sourcesById.set(compiled.id, sources);
  }

  const errors: string[] = [];
  for (const [id, sources] of sourcesById) {
    if (sources.length < 2) continue;

    errors.push(`duplicate screen id "${id}" in ${sources.join(", ")}`);
  }

  return errors;
}

// Sorted so screens are always loaded in the same order.
function findModules(dir: string): string[] {
  const modules: string[] = [];

  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const entryPath = path.join(dir, entry.name);

    if (entry.isDirectory()) {
      if (entry.name === "node_modules" || entry.name.startsWith(".")) continue;

      modules.push(...findModules(entryPath));
      continue;
    }

    if (isScreenCandidate(entry.name)) modules.push(entryPath);
  }

  return modules.sort();
}

function isScreenCandidate(name: string): boolean {
  if (name.endsWith(".d.ts")) return false;
  if (/\.test\.[jt]sx?$/.test(name)) return false;

  return MODULE_EXTENSIONS.has(path.extname(name));
}

function describe(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
