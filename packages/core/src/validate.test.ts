import { describe, it, expect } from "bun:test";
import * as fs from "fs";
import * as path from "path";

import { validateContract } from "./validate";

// Load fixtures at module scope, before describe() registration (not inside beforeAll).
// This fixes the test-collection-time ordering issue where nested describe() bodies
// run synchronously before beforeAll() executes, leaving fixture arrays empty.
const fixturesDir = path.join(process.cwd(), "schema", "fixtures");

const validFixtures: { name: string; content: any; sourceBytes: number }[] = [];
const validDir = path.join(fixturesDir, "valid");
if (fs.existsSync(validDir)) {
  fs.readdirSync(validDir)
    .filter((f) => f.endsWith(".json") && !f.endsWith(".error.json"))
    .forEach((file) => {
      const text = fs.readFileSync(path.join(validDir, file), "utf-8");
      const content = JSON.parse(text);
      const sourceBytes = Buffer.byteLength(text, "utf-8");
      validFixtures.push({ name: file.replace(".json", ""), content, sourceBytes });
    });
}

const invalidFixtures: { name: string; content: any; sourceBytes: number; expected: any }[] = [];
const invalidDir = path.join(fixturesDir, "invalid");
if (fs.existsSync(invalidDir)) {
  fs.readdirSync(invalidDir)
    .filter((f) => f.endsWith(".json") && !f.endsWith(".error.json"))
    .forEach((file) => {
      const text = fs.readFileSync(path.join(invalidDir, file), "utf-8");
      const content = JSON.parse(text);
      const sourceBytes = Buffer.byteLength(text, "utf-8");
      const errorFile = file.replace(".json", ".error.json");
      const errorPath = path.join(invalidDir, errorFile);
      if (fs.existsSync(errorPath)) {
        const expected = JSON.parse(fs.readFileSync(errorPath, "utf-8"));
        invalidFixtures.push({
          name: file.replace(".json", ""),
          content,
          sourceBytes,
          expected,
        });
      }
    });
}

describe("Contract Validator (fixture-driven)", () => {
  it("loaded fixtures", () => {
    expect(validFixtures.length).toBeGreaterThan(0);
    expect(invalidFixtures.length).toBeGreaterThan(0);
  });

  describe("Valid fixtures", () => {
    validFixtures.forEach(({ name, content, sourceBytes }) => {
      it(`accepts valid fixture: ${name}`, () => {
        const result = validateContract(content, { sourceBytes });
        expect(result.ok).toBe(true);
      });
    });
  });

  describe("Invalid fixtures", () => {
    invalidFixtures.forEach(({ name, content, sourceBytes, expected }) => {
      it(`rejects invalid fixture ${name} with code ${expected.code}`, () => {
        const result = validateContract(content, { sourceBytes });
        expect(result.ok).toBe(false);
        if (!result.ok) {
          expect(result.error.code).toBe(expected.code);
          expect(result.error.path).toBe(expected.path);
        }
      });
    });
  });

  describe("Component whitelist", () => {
    it("rejects unknown components", () => {
      const fixture = invalidFixtures.find((f) => f.name === "unknown-component");
      expect(fixture).toBeDefined();
      if (fixture) {
        const result = validateContract(fixture.content, { sourceBytes: fixture.sourceBytes });
        expect(result.ok).toBe(false);
        if (!result.ok) expect(result.error.code).toBe("UNKNOWN_COMPONENT");
      }
    });

    it("accepts known components (column, row, text, item, button, list)", () => {
      const fixture = validFixtures.find((f) => f.name === "shop");
      expect(fixture).toBeDefined();
      if (fixture) {
        const result = validateContract(fixture.content, { sourceBytes: fixture.sourceBytes });
        expect(result.ok).toBe(true);
      }
    });
  });

  describe("Binding resolution", () => {
    it("accepts bindings to declared root properties", () => {
      const fixture = validFixtures.find((f) => f.name === "shop");
      expect(fixture).toBeDefined();
      if (fixture) {
        const result = validateContract(fixture.content, { sourceBytes: fixture.sourceBytes });
        expect(result.ok).toBe(true);
      }
    });

    it("rejects bindings to undeclared properties", () => {
      const fixture = invalidFixtures.find((f) => f.name === "undeclared-binding");
      expect(fixture).toBeDefined();
      if (fixture) {
        const result = validateContract(fixture.content, { sourceBytes: fixture.sourceBytes });
        expect(result.ok).toBe(false);
        if (!result.ok) expect(result.error.code).toBe("UNDECLARED_BINDING");
      }
    });

    it("accepts bindings within list item scope", () => {
      const fixture = validFixtures.find((f) => f.name === "shop");
      expect(fixture).toBeDefined();
      if (fixture) {
        const result = validateContract(fixture.content, { sourceBytes: fixture.sourceBytes });
        expect(result.ok).toBe(true);
      }
    });

    it("validates binding type matches property type", () => {
      const fixture = invalidFixtures.find((f) => f.name === "binding-type-mismatch");
      expect(fixture).toBeDefined();
      if (fixture) {
        const result = validateContract(fixture.content, { sourceBytes: fixture.sourceBytes });
        expect(result.ok).toBe(false);
        if (!result.ok) expect(result.error.code).toBe("BINDING_TYPE_MISMATCH");
      }
    });
  });

  describe("Action validation", () => {
    it("accepts actions with declared payloads", () => {
      const fixture = validFixtures.find((f) => f.name === "shop");
      expect(fixture).toBeDefined();
      if (fixture) {
        const result = validateContract(fixture.content, { sourceBytes: fixture.sourceBytes });
        expect(result.ok).toBe(true);
      }
    });

    it("rejects undeclared actions", () => {
      const fixture = invalidFixtures.find((f) => f.name === "undeclared-action");
      expect(fixture).toBeDefined();
      if (fixture) {
        const result = validateContract(fixture.content, { sourceBytes: fixture.sourceBytes });
        expect(result.ok).toBe(false);
        if (!result.ok) expect(result.error.code).toBe("UNDECLARED_ACTION");
      }
    });

    it("validates button payload matches action schema", () => {
      const fixture = invalidFixtures.find((f) => f.name === "payload-schema-mismatch");
      expect(fixture).toBeDefined();
      if (fixture) {
        const result = validateContract(fixture.content, { sourceBytes: fixture.sourceBytes });
        expect(result.ok).toBe(false);
        if (!result.ok) expect(result.error.code).toBe("PAYLOAD_SCHEMA_MISMATCH");
      }
    });
  });

  describe("Limits", () => {
    it("accepts contracts at the limit boundary", () => {
      const boundaryFixtures = validFixtures.filter((f) => f.name.includes("limit-") && f.name.includes("boundary"));
      for (const fixture of boundaryFixtures) {
        const result = validateContract(fixture.content, { sourceBytes: fixture.sourceBytes });
        expect(result.ok).toBe(true);
      }
    });

    it("rejects contracts exceeding limits", () => {
      const exceededFixtures = invalidFixtures.filter((f) => f.name.includes("limit-") && f.name.includes("exceeded"));
      for (const fixture of exceededFixtures) {
        const result = validateContract(fixture.content, { sourceBytes: fixture.sourceBytes });
        expect(result.ok).toBe(false);
        if (!result.ok) expect(result.error.code).toBe("LIMIT_EXCEEDED");
      }
    });
  });

  describe("Error codes and paths", () => {
    it("returns stable error codes (not error messages)", () => {
      const result = validateContract(invalidFixtures[0].content, { sourceBytes: invalidFixtures[0].sourceBytes });
      if (!result.ok) {
        expect(typeof result.error.code).toBe("string");
        expect(result.error.code.length).toBeGreaterThan(0);
      }
    });

    it("returns accurate node paths (e.g., root.children[2].props.value)", () => {
      const fixture = invalidFixtures.find((f) => f.name === "undeclared-binding");
      expect(fixture).toBeDefined();
      if (fixture) {
        const result = validateContract(fixture.content, { sourceBytes: fixture.sourceBytes });
        if (!result.ok) {
          expect(result.error.path).toBe(fixture.expected.path);
        }
      }
    });
  });
});
