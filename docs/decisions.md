# Architectural Decisions

## 1. Mojang Official Mappings for Both MC Versions

**Status:** Decided in M0

**What the spec said:** "Yarn para 1.21.1 e oficiais para 26.3" (Yarn for 1.21.1, official for 26.3)

**What we're doing:** **Official Mojang mappings for both Minecraft 1.21.1 and 26.1.2**

**Why:** 
- Mojang released official mappings, and Fabric now recommends them over Yarn as the primary mappings source, even for versions like 1.21.1 that predate the unobfuscated release.
- The current upstream `FabricMC/fabric-example-mod` template uses official mappings for its 1.21.1 branch.
- Official mappings are maintained by Mojang and cover all versions uniformly, reducing maintenance burden.
- The spec's premise (§2) reflects an earlier state of the Fabric ecosystem; this change aligns with current best practices without affecting the framework's architecture.

**Impact:** 
- Both `rain-fabric-1.21.1` and `rain-fabric-26.1.2` use `loom.officialMojangMappings()` or no mappings block (for 26.1.2, which ships unobfuscated).
- No code changes required; this is purely a build-time tooling decision.
