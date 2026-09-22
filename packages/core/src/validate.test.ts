import { describe, it, expect } from "bun:test";
import * as fs from "fs";
import * as path from "path";

// Note: These tests are RED/FAILING in Phase A — validateContract() doesn't exist yet.
// They are specification for M1 Phase B implementation.

// TODO: Import validateContract once implemented in validate.ts
// import { validateContract, ValidationErrorCode } from "./validate";

// Load fixtures at module scope, before describe() registration (not inside beforeAll).
// This fixes the test-collection-time ordering issue where nested describe() bodies
// run synchronously before beforeAll() executes, leaving fixture arrays empty.
const fixturesDir = path.join(import.meta.dir, "../../../../schema/fixtures");

const validFixtures: { name: string; content: any }[] = [];
const validDir = path.join(fixturesDir, "valid");
if (fs.existsSync(validDir)) {
  fs.readdirSync(validDir)
    .filter((f) => f.endsWith(".json"))
    .forEach((file) => {
      const content = JSON.parse(
        fs.readFileSync(path.join(validDir, file), "utf-8")
      );
      validFixtures.push({ name: file.replace(".json", ""), content });
    });
}

const invalidFixtures: { name: string; content: any; expected: any }[] = [];
const invalidDir = path.join(fixturesDir, "invalid");
if (fs.existsSync(invalidDir)) {
  fs.readdirSync(invalidDir)
    .filter((f) => f.endsWith(".json") && !f.endsWith(".error.json"))
    .forEach((file) => {
      const content = JSON.parse(
        fs.readFileSync(path.join(invalidDir, file), "utf-8")
      );
      const errorFile = file.replace(".json", ".error.json");
      const errorPath = path.join(invalidDir, errorFile);
      if (fs.existsSync(errorPath)) {
        const expected = JSON.parse(fs.readFileSync(errorPath, "utf-8"));
        invalidFixtures.push({
          name: file.replace(".json", ""),
          content,
          expected,
        });
      }
    });
}

describe("Contract Validator (fixture-driven)", () => {
  describe("Valid fixtures", () => {
    validFixtures.forEach(({ name, content }) => {
      it(`accepts valid fixture: ${name}`, () => {
        // TODO: implement validate() function
        // const result = validateContract(content);
        // expect(result.ok).toBe(true);

        // Placeholder: this test is currently skipped/red
        expect.unreachable(`validateContract not yet implemented`);
      });
    });
  });

  describe("Invalid fixtures", () => {
    invalidFixtures.forEach(({ name, content, expected }) => {
      it(`rejects invalid fixture ${name} with code ${expected.code}`, () => {
        // TODO: implement validate() function
        // const result = validateContract(content);
        // expect(result.ok).toBe(false);
        // expect(result.error.code).toBe(expected.code);
        // expect(result.error.path).toBe(expected.path);

        // Placeholder: this test is currently skipped/red
        expect.unreachable(
          `validateContract not yet implemented for error case: ${expected.code}`
        );
      });
    });
  });

  describe("Component whitelist", () => {
    it("rejects unknown components", () => {
      // TODO: Test via fixture
      expect.unreachable("Test not yet implemented");
    });

    it("accepts known components (column, row, text, item, button, list)", () => {
      // TODO: Test via fixture
      expect.unreachable("Test not yet implemented");
    });
  });

  describe("Binding resolution", () => {
    it("accepts bindings to declared root properties", () => {
      // TODO: Test via fixture
      expect.unreachable("Test not yet implemented");
    });

    it("rejects bindings to undeclared properties", () => {
      // TODO: Test via fixture
      expect.unreachable("Test not yet implemented");
    });

    it("accepts bindings within list item scope", () => {
      // TODO: Test via fixture (shop fixture covers this)
      expect.unreachable("Test not yet implemented");
    });

    it("validates binding type matches property type", () => {
      // TODO: Test via fixture (e.g., string binding to int property should fail)
      expect.unreachable("Test not yet implemented");
    });
  });

  describe("Action validation", () => {
    it("accepts actions with declared payloads", () => {
      // TODO: Test via fixture
      expect.unreachable("Test not yet implemented");
    });

    it("rejects undeclared actions", () => {
      // TODO: Test via fixture
      expect.unreachable("Test not yet implemented");
    });

    it("validates button payload matches action schema", () => {
      // TODO: Test via fixture
      expect.unreachable("Test not yet implemented");
    });
  });

  describe("Limits", () => {
    it("accepts contracts at the limit boundary", () => {
      // TODO: Test via limit-boundary fixtures
      expect.unreachable("Test not yet implemented");
    });

    it("rejects contracts exceeding limits", () => {
      // TODO: Test via limit-exceeded fixtures
      expect.unreachable("Test not yet implemented");
    });
  });

  describe("Error codes and paths", () => {
    it("returns stable error codes (not error messages)", () => {
      // TODO: Verify error.code is one of the known enum values
      expect.unreachable("Test not yet implemented");
    });

    it("returns accurate node paths (e.g., root.children[2].props.value)", () => {
      // TODO: Test via fixture with explicit path expectation
      expect.unreachable("Test not yet implemented");
    });
  });
});
