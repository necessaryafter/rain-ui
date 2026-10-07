import * as fs from "fs";
import * as os from "os";
import * as path from "path";

import { findUnusedProperties } from "@rain-ui/core";

import { buildScreens, writeOutput, type Manifest } from "./build";
import { findNondeterministicCalls } from "./determinism";
import { registerScreenLoaders } from "./jsx";

const USAGE = "usage: rain <build|dev> <dir> [--out <dir>] [--check]";
const CLI_ENTRY = path.join(import.meta.dir, "../index.ts");

interface BuildOptions {
  dir: string;
  out: string;
  check: boolean;
}

export async function main(args: string[]): Promise<number> {
  const [command, ...rest] = args;
  if (command !== "build" && command !== "dev") {
    console.error(USAGE);
    return 1;
  }

  const options = parseBuildOptions(rest);
  if (!options) {
    console.error(USAGE);
    return 1;
  }

  if (command === "dev") return dev(options.dir, options.out);
  if (options.check) return check(options.dir);

  return build(options);
}

async function dev(dir: string, out: string): Promise<number> {
  let building = false;
  let queued = false;

  const rebuild = async () => {
    if (building) {
      queued = true;
      return;
    }

    building = true;

    try {
      do {
        queued = false;
        await rebuildOnce(dir, out);
      } while (queued);
    } finally {
      building = false;
    }
  };

  await rebuild();
  const watcher = fs.watch(dir, { recursive: true }, (_event, file) => {
    if (!file || isGeneratedPath(dir, out, file)) return;

    void rebuild().catch((error) => console.error(`error: ${String(error)}`));
  });

  try {
    await new Promise<void>((resolve) => {
      const stop = () => {
        process.off("SIGINT", stop);
        process.off("SIGTERM", stop);
        resolve();
      };

      process.once("SIGINT", stop);
      process.once("SIGTERM", stop);
    });
  } finally {
    watcher.close();
  }

  return 0;
}

async function rebuildOnce(dir: string, out: string): Promise<void> {
  await fs.promises.mkdir(path.dirname(out), { recursive: true });
  const temporary = await fs.promises.mkdtemp(path.join(path.dirname(out), ".rain-dev-"));

  try {
    const child = Bun.spawn([process.execPath, CLI_ENTRY, "build", dir, "--out", temporary], {
      stdout: "inherit",
      stderr: "inherit",
    });
    if (await child.exited !== 0) return;

    const manifest = JSON.parse(await fs.promises.readFile(path.join(temporary, "manifest.json"), "utf-8")) as Manifest;
    const hasSourceModules = await findSourceModules(dir);
    if (await pathExists(out) && hasSourceModules && Object.keys(manifest.screens).length === 0) return;

    await publishOutput(temporary, out);
  } finally {
    await fs.promises.rm(temporary, { recursive: true, force: true });
  }
}

async function publishOutput(temporary: string, out: string): Promise<void> {
  const backupRoot = await fs.promises.mkdtemp(path.join(path.dirname(out), ".rain-dev-backup-"));
  const backup = path.join(backupRoot, "output");
  const hadOutput = await pathExists(out);
  let preserveBackup = false;

  try {
    if (hadOutput) {
      await fs.promises.rename(out, backup);
      preserveBackup = true;
    }

    try {
      await fs.promises.rename(temporary, out);
      preserveBackup = false;
    } catch (error) {
      if (hadOutput) {
        try {
          await fs.promises.rename(backup, out);
          preserveBackup = false;
        } catch (restoreError) {
          throw new Error(`failed to restore ${out}; previous output is in ${backup}`, { cause: restoreError });
        }
      }

      throw error;
    }
  } finally {
    if (!preserveBackup) await fs.promises.rm(backupRoot, { recursive: true, force: true });
  }
}

async function pathExists(file: string): Promise<boolean> {
  try {
    await fs.promises.access(file);
    return true;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") return false;
    throw error;
  }
}

function isGeneratedPath(dir: string, out: string, file: string): boolean {
  const changed = path.resolve(dir, file);
  const relativeToOutput = path.relative(out, changed);
  const insideOutput = relativeToOutput === ""
    || (!relativeToOutput.startsWith("..") && !path.isAbsolute(relativeToOutput));
  const parts = path.relative(dir, changed).split(path.sep);

  return insideOutput || parts.includes("node_modules") || parts.some((part) => part.startsWith(".rain-dev-"));
}

async function findSourceModules(dir: string): Promise<boolean> {
  for (const entry of await fs.promises.readdir(dir, { withFileTypes: true })) {
    if (entry.name === "node_modules" || entry.name.startsWith(".")) continue;
    if (entry.isDirectory() && await findSourceModules(path.join(dir, entry.name))) return true;
    if (entry.isFile() && /\.[jt]sx?$/.test(entry.name)
      && !entry.name.endsWith(".d.ts") && !/\.test\.[jt]sx?$/.test(entry.name)) return true;
  }

  return false;
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

  registerScreenLoaders();
  const temporaryCoreLink = ensureCoreResolution(dir);
  let result: Awaited<ReturnType<typeof buildScreens>>;

  try {
    result = await buildScreens(dir);
  } finally {
    if (temporaryCoreLink) fs.unlinkSync(temporaryCoreLink);
  }

  const { screens, errors } = result;

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

function ensureCoreResolution(dir: string): string | undefined {
  const link = path.join(dir, "node_modules", "@rain-ui", "core");
  if (fs.existsSync(link)) return undefined;

  fs.mkdirSync(path.dirname(link), { recursive: true });
  fs.symlinkSync(path.join(import.meta.dir, "../../core"), link, "dir");

  return link;
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
