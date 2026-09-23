import { defineActions, defineProperties, t } from "@rain-ui/core";

// One entry per LandAction, already resolved by the server with LandChunk.scopeFor / LandPermissions.scopeFor, so
// the client never needs to know the inheritance rule (chunk override → owner profile → LandAction.defaultScope).
export const properties = defineProperties({
  title: t.string(),
  isChunkOverride: t.bool(),
  permissions: t.list(t.object({
    action: t.string(),
    actionLabel: t.string(),
    scope: t.string(),
    scopeLabel: t.string(),
    isDefault: t.bool(),
  })),
  trusted: t.list(t.object({
    playerId: t.string(),
    playerName: t.string(),
  })),
});

export const actions = defineActions({
  "land:cycle-scope": t.object({ action: t.string() }),
  "land:reset-scope": t.object({ action: t.string() }),
  "land:untrust": t.object({ playerId: t.string() }),
});
