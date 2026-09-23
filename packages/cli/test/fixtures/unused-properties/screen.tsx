import { defineActions, defineProperties, defineScreen, t } from "@rain-ui/core";

export default defineScreen({
  id: "test:unused",
  properties: defineProperties({
    listings: t.list(t.object({
      id: t.string(),
      name: t.string(),
      sellerId: t.string(),
      bids: t.list(t.object({ amount: t.double() })),
    })),
  }),
  actions: defineActions({
    "test:open": t.object({ listingId: t.string() }),
  }),
  render: (p, a) => (
    <list source={p.listings}>
      {(listing) => (
        <button action={a["test:open"]} payload={{ listingId: listing.id }}>
          <text value={listing.name} />
        </button>
      )}
    </list>
  ),
});
