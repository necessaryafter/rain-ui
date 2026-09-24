import { defineActions, defineProperties, t } from "@rain-ui/core";

// The view model of one GTS page, built by the plugin's adapter from GtsListing. Money is formatted on the server
// (currency symbol, separators); the raw value stays on the server and actions only carry the listing id.
const BidSummary = t.object({
  currentBidLabel: t.string().optional(),
  currentBidderName: t.string().optional(),
  startingBidLabel: t.string().optional(),
  bidCount: t.int().default(0),
});

const ListingCard = t.object({
  id: t.string(),
  pokemon: t.item(),
  pokemonName: t.string(),
  typeIcon: t.asset(), // "fire", "water" or "grass": the name of one of the screen's assets, never a hash or URL
  sellerName: t.string(),

  saleType: t.string(),
  status: t.string(),
  statusLabel: t.string(),

  priceLabel: t.string().optional(),
  bidSummary: BidSummary,

  timeLeft: t.string(),
  awaitingClaimLabel: t.string().optional(),
  isMine: t.bool(),
});

export const properties = defineProperties({
  pageLabel: t.string(),
  isFirstPage: t.bool(),
  isLastPage: t.bool(),
  listings: t.list(ListingCard),
});

export const actions = defineActions({
  "gts:open": t.object({ listingId: t.string() }),
  "gts:page": t.object({ direction: t.string() }),
});
