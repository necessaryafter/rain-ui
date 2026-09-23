import { defineActions, defineProperties, t } from "@rain-ui/core";

// Built per viewer: the permission flags come from City.isMayor / canManageTreasury / isMayorOrVice for the player
// who opened the screen, so a mayor and a resident get different properties for the same city.
export const properties = defineProperties({
  name: t.string(),
  banner: t.item().optional(),
  mayorName: t.string(),
  developmentLevel: t.int(),
  taxRate: t.int(),
  treasuryLabel: t.string(),

  joinPolicy: t.string(),
  joinPolicyLabel: t.string(),

  government: t.object({
    culture: t.string().default("Não definida"),
    religion: t.string().default("Não definida"),
    governmentName: t.string().optional(),
  }),

  roles: t.list(t.object({
    playerName: t.string(),
    typeLabel: t.string(),
  })),

  joinRequests: t.list(t.object({
    playerId: t.string(),
    playerName: t.string(),
  })),

  canManageCity: t.bool(),
  canManageTreasury: t.bool(),
});

export const actions = defineActions({
  "city:cycle-join-policy": t.object({}),
  "city:accept-request": t.object({ playerId: t.string() }),
  "city:deny-request": t.object({ playerId: t.string() }),
  "city:change-tax": t.object({ step: t.int() }),
});
