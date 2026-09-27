import { afterEach, describe, expect, it } from "bun:test";
import * as fs from "fs";
import * as os from "os";
import * as path from "path";

const projectRoot = path.join(import.meta.dir, "../../..");
const cliPath = path.join(projectRoot, "packages/cli/index.ts");
const processes: ReturnType<typeof Bun.spawn>[] = [];

function tempDir(): string {
  return fs.mkdtempSync(path.join(os.tmpdir(), "rain-dev-"));
}

function writeScreen(dir: string, label: string): void {
  fs.writeFileSync(path.join(dir, "screen.tsx"), `
    import { defineScreen, t } from "@rain-ui/core";
    export default defineScreen({
      id: "test:dev",
      properties: {}, actions: {},
      render: () => <text value="${label}" />,
    });
  `);
}

function startDev(source: string, out: string): ReturnType<typeof Bun.spawn> {
  const child = Bun.spawn(["bun", cliPath, "dev", source, "--out", out], {
    cwd: projectRoot,
    stdout: "pipe",
    stderr: "pipe",
  });
  processes.push(child);
  return child;
}

async function waitForFile(file: string, predicate: (text: string) => boolean): Promise<string> {
  const deadline = Date.now() + 5_000;
  while (Date.now() < deadline) {
    if (fs.existsSync(file)) {
      const text = fs.readFileSync(file, "utf-8");
      if (predicate(text)) return text;
    }
    await Bun.sleep(25);
  }
  throw new Error(`Timed out waiting for ${file}`);
}

afterEach(() => {
  for (const child of processes.splice(0)) child.kill();
});

describe("rain dev", () => {
  it("publishes the initial build, rebuilds source changes, and removes deleted screens", async () => {
    const source = tempDir();
    const out = tempDir();
    writeScreen(source, "first");
    const child = startDev(source, out);
    const contract = path.join(out, "test/dev.json");

    await waitForFile(contract, (text) => text.includes("first"));
    writeScreen(source, "second");
    await waitForFile(contract, (text) => text.includes("second"));
    fs.unlinkSync(path.join(source, "screen.tsx"));
    await waitForFile(path.join(out, "manifest.json"), (text) => !text.includes("test:dev"));
    expect(child.exitCode).toBeNull();
  });

  it("keeps the last published output after an invalid build and recovers on the next change", async () => {
    const source = tempDir();
    const out = tempDir();
    writeScreen(source, "stable");
    startDev(source, out);
    const contract = path.join(out, "test/dev.json");

    await waitForFile(contract, (text) => text.includes("stable"));
    fs.writeFileSync(path.join(source, "screen.tsx"), "export default {};");
    await Bun.sleep(150);
    expect(fs.readFileSync(contract, "utf-8")).toContain("stable");
    writeScreen(source, "recovered");
    await waitForFile(contract, (text) => text.includes("recovered"));
  });
});
