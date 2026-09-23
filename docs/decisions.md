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

## 5. Lombok in the pure Java modules

**Status:** Decided in M1

**What:** `rain-protocol`, `rain-server` and `rain-client-core` depend on Lombok (`compileOnly` + annotation
processor, version in `gradle.properties`). The Fabric modules do not, until one needs it.

**Why:** Approved by the maintainer to remove hand-written getters and constructors. It is compile-time only, so it
adds nothing to the runtime classpath of the mod.

## 6. Optional values, conditions and the view model

**Status:** Decided in M1, driven by real plugin models (GTS listings, cities, land claims)

**What:**

| Topic                 | Decision                                                                                                   |
|-----------------------|------------------------------------------------------------------------------------------------------------|
| Optional              | `t.x().optional()` → `{ "kind": "x", "optional": true }`. Absent and `null` both mean "no value".          |
| Default               | `t.x().default(v)` for `string`/`int`/`long`/`bool`/`double` only; implies optional; used when absent or `null`, not for `""`; `properties` only, never in an action schema. |
| Decimal               | `double` for data and payload. Not accepted by `text.value` or `match`; the server sends a label.          |
| `text.value`          | String literal, or binding of `string`/`int`/`long` (raw decimal, locale-independent).                    |
| `color`               | Literal `#RRGGBB` or string binding; a bad runtime value falls back to the default color.                 |
| Optional binding      | Allowed on every prop, with a per-prop default: `""`, no item, `false`, `[]`, default color.              |
| `show`                | `when` binds any type. `bool` uses its value; other types show the content when present and not `""`/`[]`. |
| `match`               | `value` binds `string`/`int`/`long`/`bool`; `case is={literal}` children and at most one trailing `default`, which also takes an absent value. |
| Payload               | A required payload field rejects a binding to an optional property without a default.                    |
| Misuse                | Using a binding as a JS value in `render` is a build error; a declared property never bound is a warning. |

`show`'s fallback and `match`'s cases are child nodes (`fallback`, `case`, `default`), so `ComponentNode` keeps its
`{ type, props, children }` shape. New error codes: `INVALID_DEFAULT`, `MISPLACED_COMPONENT`, `DUPLICATE_CASE`.

**Why:** The render runs once at build time, where a binding is a path marker and not a value, so JS conditions over
data cannot work and would silently freeze the wrong result. Conditions over data have to be declarative for the
client to evaluate. Formatting stays on the server: the client never learns currency symbols, locales or the
inheritance rules of a plugin's model.

## 7. Open items for later milestones

- **M2:** server-initiated updates (update one instance, or every open instance of a screen); a Kotlin-friendly
  adapter API where optional fields accept `null`; a dev warning when a properties send is large or a screen is
  updated too often.
- **M3:** a `countdown` component taking an epoch-millis `long`, so timers do not need one update per second; local
  client state for purely visual toggles such as tabs; sending only what changed in an update; a declarative number
  format prop.
- **M2.4:** confirm in the Loom sources that `lwjgl-stb` and `lwjgl-freetype` ship with both target versions, since the
  image and font decoders rely on them.
- **After v0:** video and WebP as new asset types.
- **After v0:** a type-aware lint rule (`no-binding-in-condition`) for `if (p.x)`, `!p.x` and `p.x && …`, which the
  build cannot detect at runtime.

## 8. Static content over HTTP, assets in the contract

**Status:** Decided before M2, replacing contract chunks over the game connection

**What:**

| Topic                  | Decision                                                                                                  |
|------------------------|-----------------------------------------------------------------------------------------------------------|
| Transport              | HTTP only downloads static, hash-addressed content (contracts and assets). Per-player data (properties, interactions, responses) stays on the game connection. |
| HTTP protocol          | `GET <assetBaseUrl>/<sha256>` returns the bytes. Any static file server works.                          |
| Hosting                | A base server in `rain-server` (JDK `HttpServer`, no dependency) serves `dist/assets/`; devs can host it anywhere and set `assetBaseUrl`. |
| Integrity              | The contract hash arrives over the game connection and the contract pins every asset hash, so one hash verifies all content. |
| HTTP vs HTTPS          | Both accepted; integrity comes from the hash. No cross-host redirects, no cookies or credentials, timeouts. |
| Consent                | One prompt per server showing the host and size, like the vanilla resource pack. Refusing sends `ScreenFailed`. |
| Formats (v0)           | PNG, JPEG, GIF (animated), TTF, OTF, decodable with `lwjgl-stb` and `lwjgl-freetype` already in the client. |
| Limits                 | 8 MiB per asset; 256 assets and 64 MiB per contract; images up to 4096 px per side; GIF up to 512 frames and 128 MiB decoded; client cache 512 MiB LRU. |
| Contract format        | `assets: { <sha256>: { type, bytes, width?, height?, frames? } }`, `assetNames: { name: <sha256> }`, references as `{ "$asset": <sha256> }`. |
| Server-chosen assets   | `t.asset()` properties carry a name from `defineScreen({ assets })`, never a hash or URL.               |
| Components             | `image` moves into v0; `text` gains `font`.                                                              |
| Packets                | `RequestContract` and `ContractChunk` are removed; server `Hello` gains `assetBaseUrl`; client `ScreenFailed(instanceId, reason)` is added. |
| Items in properties    | Base64 of the vanilla network ItemStack codec, opaque to the protocol.                                   |
| Optional in Java API   | `findX` accessors return `null` annotated with JSpecify `@Nullable` (compileOnly), so Kotlin sees `T?`.  |
| New limits             | Property strings up to 4096 characters; screen and action ids in packets up to 256 bytes.                |

**Why:** Rich UIs need fonts, images and GIFs now and video later. Vanilla resource packs cap textures in atlases, have
no video, and reload every client resource on each push, which is unusable for UI. Downloading by hash keeps the
spirit of closed decision 4: the vanilla resource pack already lets a server point the client at any URL, with a hash
and a prompt, and this adds nothing beyond that. Keeping per-player data on the game connection keeps ordering,
revisions and authentication where they already work.

**Impact:** Spec §3.4, §4, §5.2–5.3, §6, §8, §10 and §11 were rewritten. A new milestone, M1.5, adds assets to the
build before M2. The disk cache and `image` move from M3 into v0.
