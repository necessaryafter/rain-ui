import * as fs from "fs";
import * as os from "os";
import * as path from "path";

import { findUnusedProperties } from "@rain-ui/core";

import { buildScreens, writeOutput, type Manifest } from "./build";
import { findNondeterministicCalls } from "./determinism";
import { registerJsxPlugin } from "./jsx";

const USAGE = "usage: rain build <dir> [--out <dir>] [--check]";
const CLI_ENTRY = path.join(import.meta.dir, "../index.ts");

interface BuildOptions {
  dir: string;
  out: string;
  check: boolean;
}

export async function main(args: string[]): Promise<number> {
  const [command, ...rest] = args;
  if (command !== "build") {
    console.error(USAGE);
    return 1;
  }

  const options = parseBuildOptions(rest);
  if (!options) {
    console.error(USAGE);
    return 1;
  }

  if (options.check) return check(options.dir);

  return build(options);
}

function parseBuildOptions(args: string[]): BuildOptions | undefined {
  let dir: string | undefined;
  let out = path.resolve("dist");
  let check = false;

  for (let i = 0; i < args.length; i++) {
    const arg = args[i];

    if (arg === "--check") {
      check = true;
      continue;
    }

    if (arg === "--out") {
      const value = args[++i];
      if (!value) return undefined;

      out = path.resolve(value);
      continue;
    }

    if (arg.startsWith("-") || dir) return undefined;

    dir = path.resolve(arg);
  }

  if (!dir) return undefined;

  return { dir, out, check };
}

async function build({ dir, out }: BuildOptions): Promise<number> {
  if (!fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) {
    console.error(`error: ${dir} is not a directory`);
    return 1;
  }

  registerJsxPlugin();

  const { screens, errors } = await buildScreens(dir);

  const warnings = await findNondeterministicCalls(screens.map((screen) => screen.source));
  for (const { file, line, call } of warnings) {
    const location = line === undefined ? path.relative(dir, file) : `${path.relative(dir, file)}:${line}`;
    console.error(`warning: ${call} in ${location}: the value is frozen at build time`);
  }

  for (const { source, compiled } of screens) {
    for (const property of findUnusedProperties(compiled.contract)) {
      console.error(
        `warning: unused property ${property} in ${compiled.id} (${path.relative(dir, source)}): ` +
        `the server would send it but the screen never shows it`,
      );
    }
  }

  if (errors.length > 0) {
    for (const error of errors) {
      console.error(`error: ${error}`);
    }

    return 1;
  }

  writeOutput(out, screens);
  console.log(`built ${screens.length} screen(s) into ${out}`);

  return 0;
}

// Two separate processes, so state that leaks between renders in one process cannot hide nondeterminism.
async function check(dir: string): Promise<number> {
  const first = runBuildProcess(dir);
  if (!first.manifest) {
    process.stderr.write(first.stderr);
    return 1;
  }

  const second = runBuildProcess(dir);
  if (!second.manifest) {
    process.stderr.write(second.stderr);
    return 1;
  }

  process.stderr.write(first.stderr);

  const mismatches = compareManifests(first.manifest, second.manifest);
  for (const id of mismatches) {
    console.error(`error: hash of ${id} differs between two builds; the screen is not deterministic`);
  }

  if (mismatches.length > 0) return 1;

  console.log(`check passed: ${Object.keys(first.manifest.screens).length} screen(s) built identically twice`);
  return 0;
}

function runBuildProcess(dir: string): { manifest: Manifest | undefined; stderr: string } {
  const out = fs.mkdtempSync(path.join(os.tmpdir(), "rain-check-"));

  try {
    const result = Bun.spawnSync([process.execPath, CLI_ENTRY, "build", dir, "--out", out]);
    const stderr = result.stderr.toString();
    if (result.exitCode !== 0) return { manifest: undefined, stderr };

    const manifest = JSON.parse(fs.readFileSync(path.join(out, "manifest.json"), "utf-8"));
    return { manifest, stderr };
  } finally {
    fs.rmSync(out, { recursive: true, force: true });
  }
}

function compareManifests(first: Manifest, second: Manifest): string[] {
  const ids = new Set([...Object.keys(first.screens), ...Object.keys(second.screens)]);

  return [...ids]
    .filter((id) => first.screens[id]?.sha256 !== second.screens[id]?.sha256)
    .sort();
}
