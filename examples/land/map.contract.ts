import { defineActions, defineProperties, t } from "@rain-ui/core";

// A square of chunks around the player, row by row. The server picks each cell's color from who owns the chunk
// (you, your city, another player, nobody), so the legend lives on the server too.
export const properties = defineProperties({
  center: t.string(),
  rows: t.list(t.object({
    cells: t.list(t.object({
      x: t.int(),
      z: t.int(),
      color: t.string(),
      label: t.string().optional(),
      canClaim: t.bool(),
    })),
  })),
});

export const actions = defineActions({
  "land:claim": t.object({ x: t.int(), z: t.int() }),
  "land:move": t.object({ dx: t.int(), dz: t.int() }),
});
