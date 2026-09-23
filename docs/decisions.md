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
