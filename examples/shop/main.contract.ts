import { defineProperties, defineActions, t } from "@rain-ui/core";

export const properties = defineProperties({
  items: t.list(
    t.object({
      id: t.string(),
      name: t.string(),
      priceLabel: t.string(),
      icon: t.item(),
      locked: t.bool(),
    })
  ),
});

export const actions = defineActions({
  "shop:buy": t.object({ itemId: t.string() }),
});
