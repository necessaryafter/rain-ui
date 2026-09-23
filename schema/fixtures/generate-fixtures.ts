import * as fs from "fs";
import * as path from "path";

const fixturesDir = path.join(import.meta.dir);

// Helper to create minimal contract
function createMinimalContract(root: any) {
    return {
        schemaVersion: 0,
        id: "test:fixture",
        properties: {},
        actions: {},
        root,
    };
}

// Helper to create text node
function textNode(value: any) {
    return {
        type: "text",
        props: { value },
        children: [],
    };
}

// Hand-authored fixtures (invalid)
const invalidFixtures: Record<string, { contract: any; expected: any }> = {
    "invalid-prop-type": {
        contract: createMinimalContract({
            type: "text",
            props: { value: 123 }, // number instead of string
            children: [],
        }),
        expected: { code: "INVALID_PROP_TYPE", path: "root.props.value" },
    },
    "binding-type-mismatch": {
        contract: {
            schemaVersion: 0,
            id: "test:binding-mismatch",
            properties: { myBool: { kind: "bool" } },
            actions: {},
            root: {
                type: "text",
                props: { value: { $bind: "myBool" } }, // bool binding to string prop
                children: [],
            },
        },
        expected: { code: "BINDING_TYPE_MISMATCH", path: "root.props.value" },
    },
    "undeclared-action": {
        contract: createMinimalContract({
            type: "button",
            props: { action: "test:nonexistent", disabled: false },
            children: [textNode("Click")],
        }),
        expected: { code: "UNDECLARED_ACTION", path: "root.props.action" },
    },
    "payload-schema-mismatch": {
        contract: {
            schemaVersion: 0,
            id: "test:payload-mismatch",
            properties: {},
            actions: { "test:buy": { kind: "object", fields: { itemId: { kind: "string" } } } },
            root: {
                type: "button",
                props: {
                    action: "test:buy",
                    payload: { itemId: 123 }, // number instead of string
                    disabled: false,
                },
                children: [textNode("Buy")],
            },
        },
        expected: { code: "PAYLOAD_SCHEMA_MISMATCH", path: "root.props.payload.itemId" },
    },
    "invalid-id": {
        contract: createMinimalContract({
            ...textNode("Test"),
            root: {
                type: "text",
                props: { value: "Test" },
                children: [],
            },
        }),
        expected: { code: "INVALID_ID", path: "id" },
    },
};

// Correct the invalid-id contract
invalidFixtures["invalid-id"].contract = {
    schemaVersion: 0,
    id: "InvalidId", // Invalid format (no colon)
    properties: {},
    actions: {},
    root: textNode("Test"),
};

// Generate limit fixtures
function generateLimitFixtures() {
    // MAX_NODES (2000/2001) - nodeCount increments for each node
    // Valid: 1999 children + 1 row = 2000 total
    const maxNodesValid = createMinimalContract({
        type: "row",
        props: {},
        children: Array(1999)
            .fill(0)
            .map(() => ({ type: "text", props: { value: "x" }, children: [] })),
    });

    // Invalid: 2000 children + 1 row = 2001 total
    const maxNodesInvalid = createMinimalContract({
        type: "row",
        props: {},
        children: Array(2000)
            .fill(0)
            .map(() => ({ type: "text", props: { value: "x" }, children: [] })),
    });

    // MAX_DEPTH (32/33) - depth increments at each recursion level
    // Valid: 32 levels of nesting (root=0, 32 columns deep reaches depth 32, which is not > MAX_DEPTH)
    let depthValid: any = textNode("deep");
    for (let i = 0; i < 32; i++) {
        depthValid = { type: "column", props: {}, children: [depthValid] };
    }

    // Invalid: 33 levels of nesting (reaches depth 33, which is > MAX_DEPTH)
    let depthInvalid: any = textNode("deep");
    for (let i = 0; i < 33; i++) {
        depthInvalid = { type: "column", props: {}, children: [depthInvalid] };
    }

    // MAX_ACTIONS (256/257)
    const actionsValid: Record<string, any> = {};
    for (let i = 0; i < 256; i++) {
        actionsValid[`test:action-${i}`] = { kind: "object", fields: {} };
    }

    const actionsInvalid: Record<string, any> = {};
    for (let i = 0; i < 257; i++) {
        actionsInvalid[`test:action-${i}`] = { kind: "object", fields: {} };
    }

    // MAX_STRING_LENGTH (1024/1025)
    const str1024 = "x".repeat(1024);
    const str1025 = "x".repeat(1025);

    return {
        "limit-max-nodes-boundary": { contract: maxNodesValid, limit: "MAX_NODES" },
        "limit-max-nodes-exceeded": { contract: maxNodesInvalid, limit: "MAX_NODES" },
        "limit-max-depth-boundary": { contract: { ...createMinimalContract(depthValid), root: depthValid }, limit: "MAX_DEPTH" },
        "limit-max-depth-exceeded": { contract: { ...createMinimalContract(depthInvalid), root: depthInvalid }, limit: "MAX_DEPTH" },
        "limit-max-actions-boundary": {
            contract: {
                schemaVersion: 0,
                id: "test:max-actions-boundary",
                properties: {},
                actions: actionsValid,
                root: textNode("test"),
            },
            limit: "MAX_ACTIONS",
        },
        "limit-max-actions-exceeded": {
            contract: {
                schemaVersion: 0,
                id: "test:max-actions-exceeded",
                properties: {},
                actions: actionsInvalid,
                root: textNode("test"),
            },
            limit: "MAX_ACTIONS",
        },
        "limit-max-string-length-boundary": {
            contract: createMinimalContract(textNode(str1024)),
            limit: "MAX_STRING_LENGTH",
        },
        "limit-max-string-length-exceeded": {
            contract: createMinimalContract(textNode(str1025)),
            limit: "MAX_STRING_LENGTH",
        },
    };
}

// Write fixtures
function writeFixtures() {
    // Write invalid hand-authored
    for (const [name, data] of Object.entries(invalidFixtures)) {
        const jsonPath = path.join(fixturesDir, "invalid", `${name}.json`);
        const errorPath = path.join(fixturesDir, "invalid", `${name}.error.json`);

        fs.writeFileSync(jsonPath, JSON.stringify(data.contract, null, 2) + "\n");
        fs.writeFileSync(errorPath, JSON.stringify(data.expected, null, 2) + "\n");

        console.log(`✓ ${name}`);
    }

    // Write limit fixtures
    const limitFixtures = generateLimitFixtures();
    for (const [name, data] of Object.entries(limitFixtures)) {
        const isValid = !name.includes("exceeded");
        const subdir = isValid ? "valid" : "invalid";
        const jsonPath = path.join(fixturesDir, subdir, `${name}.json`);
        const errorPath = isValid ? null : path.join(fixturesDir, subdir, `${name}.error.json`);

        // Minify if MAX_CONTRACT_BYTES, otherwise pretty-print
        const jsonStr = JSON.stringify(data.contract);
        fs.writeFileSync(jsonPath, jsonStr + "\n");

        if (errorPath) {
            const error = { code: "LIMIT_EXCEEDED", path: "root" };
            fs.writeFileSync(errorPath, JSON.stringify(error, null, 2) + "\n");
        }

        console.log(`✓ ${name}`);
    }
}

writeFixtures();
console.log("\n✓ All fixtures generated!");
