import { describe, it, expect } from "bun:test";
import { version } from "./index";

describe("@rain-ui/core", () => {
  it("exports a version", () => {
    expect(version).toBeDefined();
  });
});
