import { defineScreen } from "@rain-ui/core";
import { ListingCard } from "./components/ListingCard";
import { actions, properties } from "./listings.contract";

export default defineScreen({
  id: "gts:listings",
  properties,
  actions,
  render: (p, a) => (
    <column gap={4} padding={8}>
      <text value="GTS" />

      <show when={p.listings} fallback={<text value="Nenhum anúncio no momento" />}>
        <list source={p.listings}>
          {(listing) => <ListingCard listing={listing} open={a["gts:open"]} />}
        </list>
      </show>

      <row gap={4}>
        <button action={a["gts:page"]} payload={{ direction: "previous" }} disabled={p.isFirstPage}>
          <text value="<" />
        </button>
        <text value={p.pageLabel} />
        <button action={a["gts:page"]} payload={{ direction: "next" }} disabled={p.isLastPage}>
          <text value=">" />
        </button>
      </row>
    </column>
  ),
});
