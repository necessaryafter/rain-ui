import * as fs from "fs";
import * as path from "path";
import { validateContract } from "../../packages/core/src/validate";

const fixturesDir = path.join(process.cwd(), "schema/fixtures");

const limitFixtures = [
    "limit-max-nodes-exceeded",
    "limit-max-depth-exceeded",
    "limit-max-actions-exceeded",
    "limit-max-string-length-exceeded",
];

for (const name of limitFixtures) {
    const jsonPath = path.join(fixturesDir, "invalid", `${name}.json`);
    const errorPath = path.join(fixturesDir, "invalid", `${name}.error.json`);

    const text = fs.readFileSync(jsonPath, "utf-8");
    const content = JSON.parse(text);
    const sourceBytes = Buffer.byteLength(text, "utf-8");

    const result = validateContract(content, { sourceBytes });

    if (!result.ok) {
        const error = { code: result.error.code, path: result.error.path };
        fs.writeFileSync(errorPath, JSON.stringify(error, null, 2) + "\n");
        console.log(`✓ Fixed ${name}: path is now "${result.error.path}"`);
    } else {
        console.log(`✗ ${name} did not fail validation!`);
    }
}
