import { defineActions, defineProperties, defineScreen, t } from "@rain-ui/core";

const properties = defineProperties({
  items: t.list(
    t.object({
      id: t.string(),
      name: t.string(),
      priceLabel: t.string(),
      icon: t.item(),
      locked: t.bool(),
    }),
  ),
});

const actions = defineActions({
  "shop:buy": t.object({ itemId: t.string() }),
});

export default defineScreen({
  id: "shop:main",
  properties,
  actions,
  render: (p, a) => (
    <column gap={4} padding={8}>
      <text value="Loja" />
      <list source={p.items}>
        {(item) => (
          <row gap={4}>
            <item value={item.icon} />
            <column gap={2}>
              <text value={item.name} />
              <text value={item.priceLabel} />
            </column>
            <button
              action={a["shop:buy"]}
              payload={{ itemId: item.id }}
              disabled={item.locked}
            >
              <text value="Comprar" />
            </button>
          </row>
        )}
      </list>
    </column>
  ),
});
