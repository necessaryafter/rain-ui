# Properties are the screen's view model

`defineProperties` declares what the server sends **so a screen can display it as is**. It is not your database
document. The render runs once at build time and the client has no logic, so everything the screen shows has to
arrive ready: formatted, resolved for the player looking at it, and with only the fields the screen uses.

## Wrong: the database document

```ts
// Mirrors GtsListing from the plugin
export const properties = defineProperties({
  listings: t.list(t.object({
    id: t.string(),
    sellerId: t.string(),        // a UUID the screen never shows
    saleType: t.string(),
    price: t.double().optional(), // 1250.5 — the client cannot format money
    bidData: t.object({
      bids: t.list(t.object({    // the whole bid history on every card of every page
        bidderId: t.string(),
        amount: t.double(),
        placedAt: t.string(),    // an Instant the client cannot turn into "3 min ago"
      })),
    }),
    expiresAt: t.string(),
  })),
});
```

```tsx
// Looks fine and builds wrong: p.x here is a path marker, not a value.
<text value={`R$ ${card.price}`} />                    // build error
{card.bidData.bids.length === 0 && <text value="Sem lances" />}  // build error
<button disabled={!card.isMine} />                     // no error, frozen as `false` forever
```

## Right: what this screen shows, for this player

```ts
export const properties = defineProperties({
  listings: t.list(t.object({
    id: t.string(),                        // used in the open action's payload
    pokemonName: t.string(),
    saleType: t.string(),                  // "FIXED_PRICE" | "AUCTION", for <match>
    priceLabel: t.string().optional(),     // "R$ 1.250,50", formatted by the server
    bidSummary: t.object({
      currentBidLabel: t.string().optional(),
      bidCount: t.int().default(0),
    }),
    timeLeft: t.string(),                  // "expira em 3h"
    isMine: t.bool(),                      // computed for the viewer
  })),
});
```

```tsx
<match value={card.saleType}>
  <case is="FIXED_PRICE"><text value={card.priceLabel} /></case>
  <case is="AUCTION">
    <show when={card.bidSummary.currentBidLabel} fallback={<text value="Sem lances" />}>
      <text value={card.bidSummary.currentBidLabel} />
    </show>
  </case>
</match>
```

## Rules of thumb

- **Format on the server.** Money, dates, durations, plurals, translations.
- **Resolve for the viewer.** `isMine`, `canManageTreasury`, `canClaim`: call your model's methods in the adapter and
  send the result.
- **Send enums as value + label.** The value drives `<match>` and payloads; the label is what the player reads.
- **Replace ids with what the player sees.** A `List<UUID>` becomes `{ playerId, playerName }`; the id goes in a
  payload, the name on screen.
- **Resolve inherited values.** If a value comes from an override or a default, send the effective value and, if the
  screen needs it, a flag saying where it came from.
- **Send intent in actions, not values.** `{ listingId, step: "+10%" }`, never an amount the client computed.

## What the build catches for you

| Mistake                                              | Result          |
|------------------------------------------------------|-----------------|
| `` `${p.x}` ``, `p.x + "…"`, `p.x > 3`               | build error     |
| `p.list.length`, `p.list.map(...)`                   | build error, points to `<list>` |
| `p.object.missing`                                   | build error     |
| A declared property the screen never binds           | build warning   |
| A key the server sends that is not declared          | error on the server at `open` |
| `if (p.x)`, `!p.x`, `p.x && …`                       | **not caught** — the value is frozen at build time |
