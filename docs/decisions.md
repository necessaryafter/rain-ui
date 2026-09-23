# Architectural Decisions

## 1. Mojang Official Mappings for Both MC Versions

**Status:** Decided in M0

**What the spec said:** "Yarn para 1.21.1 e oficiais para 26.3" (Yarn for 1.21.1, official for 26.3)

**What we're doing:** **Official Mojang mappings for both Minecraft 1.21.1 and 26.1.2**

**Why:**

- Mojang released official mappings, and Fabric now recommends them over Yarn as the primary mappings source, even for
  versions like 1.21.1 that predate the unobfuscated release.
- The current upstream `FabricMC/fabric-example-mod` template uses official mappings for its 1.21.1 branch.
- Official mappings are maintained by Mojang and cover all versions uniformly, reducing maintenance burden.
- The spec's premise (§2) reflects an earlier state of the Fabric ecosystem; this change aligns with current best
  practices without affecting the framework's architecture.

**Impact:**

- Both `rain-fabric-1.21.1` and `rain-fabric-26.1.2` use `loom.officialMojangMappings()` or no mappings block (for
  26.1.2, which ships unobfuscated).
- No code changes required; this is purely a build-time tooling decision.

## 2. Binding scope limitation (v0)

**Status:** Decided in M1

**What:** Bindings inside a `list`'s item template cannot reach root properties or outer list scopes. Only the innermost
list's item scope is bindable. Nested `list(list(...))` only exposes the innermost item.

**Why:** Keeps the JSON binding format simple (`{"$bind":"path"}` with no scope prefix, scope determined by tree
position), and keeps both the TS and Java validators straightforward without needing to track a scope stack through the
tree walk.

**Example of what's NOT supported in v0:** A list of items where each row needs access to a root-level currency symbol.
This would require either: (1) pre-computing the full formatted text on the server (recommended for v0), or (2) adding
root-property access from item scope in a future milestone.

**Impact:** Developers must pass all needed data through the item properties themselves, not try to reference outer
scope. This aligns with the pattern shown in the spec's shop example.

## 3. JSON depth limit, error paths and build output (v0)

**Status:** Decided in M1

**What:**

- `MAX_JSON_DEPTH` is **128**, not 32. It applies to every JSON document (contract, properties, payload) and is
  checked by a streaming counter before the document is parsed into a tree.
- Depth counts containers: the top-level object or array is 1, scalars add nothing. `{"a":{"b":1}}` has depth 2.
- An error that applies to the whole document (byte size, JSON depth) has the path `root`.
- Properties are limited to 256 KiB and 1000 elements per array; interaction payloads to 8 KiB and depth 8. These
  checks do not look at the schema.
- `rain build <dir>` writes to `<cwd>/dist` by default; `--out <dir>` overrides it. The manifest is:

```json
{
  "schemaVersion": 0,
  "screens": {
    "shop:main": { "file": "shop/main.json", "sha256": "<hex>" }
  }
}
```

**Why:** With a limit of 32, the contract structure itself exceeds it: each component level adds two JSON levels (the
node object and its `children` array), so a tree at the component limit of 32 reaches about 66. The JSON depth limit
exists to protect the parser from stack exhaustion; domain nesting is limited by `MAX_DEPTH` (32 components) and
`MAX_PAYLOAD_DEPTH` (8).

**Impact:** `Limits.MAX_JSON_DEPTH` changes to 128 in both `rain-protocol` and `@rain-ui/core`. The manifest carries
its own `schemaVersion` so the format can change without guessing.

## 4. `rain build` discovery and determinism scan

**Status:** Decided in M1

**What:**

- `rain build <dir>` imports every `.ts`/`.tsx`/`.js`/`.jsx` module under `<dir>` recursively, skipping `node_modules`,
  dot directories, `*.d.ts` and `*.test.*`.
- The determinism scan follows relative and absolute imports only. A bare specifier (`"lodash"`, `"@rain-ui/core"`)
  is a dependency and is not scanned, whether or not it resolves into `node_modules`.
- The CLI configures JSX itself instead of reading the caller's `tsconfig.json`, so a screen builds the same from any
  working directory.

**Why:** Screens can live in subdirectories. Classifying by specifier instead of by resolved path keeps workspace
packages (symlinked outside `node_modules`) out of the scan, same as installed ones. Bun picks the tsconfig from the
process, not from each file, so relying on it made the build depend on where it was run.
