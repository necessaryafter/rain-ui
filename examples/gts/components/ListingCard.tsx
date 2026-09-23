export function ListingCard({ listing, open }: { listing: any; open: any }) {
  return (
    <button action={open} payload={{ listingId: listing.id }}>
      <row gap={4}>
        <item value={listing.pokemon} />
        <column gap={2}>
          <text value={listing.pokemonName} />
          <text value={listing.sellerName} color="#AAAAAA" />

          <match value={listing.saleType}>
            <case is="FIXED_PRICE">
              <text value={listing.priceLabel} />
            </case>
            <case is="AUCTION">
              <column>
                <show
                  when={listing.bidSummary.currentBidLabel}
                  fallback={<text value={listing.bidSummary.startingBidLabel} />}
                >
                  <text value={listing.bidSummary.currentBidLabel} />
                  <text value={listing.bidSummary.currentBidderName} color="#AAAAAA" />
                </show>
                <text value={listing.bidSummary.bidCount} />
              </column>
            </case>
          </match>

          <match value={listing.status}>
            <case is="ACTIVE">
              <text value={listing.timeLeft} />
            </case>
            <case is="AWAITING_CLAIM">
              <text value={listing.awaitingClaimLabel} color="#FFAA00" />
            </case>
            <default>
              <text value={listing.statusLabel} />
            </default>
          </match>

          <show when={listing.isMine}>
            <text value="Seu anúncio" color="#55FF55" />
          </show>
        </column>
      </row>
    </button>
  );
}
