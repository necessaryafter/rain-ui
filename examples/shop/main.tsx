import { defineScreen } from "@rain-ui/core";
import { properties, actions } from "./main.contract";
import { PriceTag } from "./components/PriceTag";

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
              <PriceTag price={item.priceLabel} />
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
