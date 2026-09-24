import { defineScreen } from "@rain-ui/core";
import { ListingCard } from "./components/ListingCard";
import titleFont from "./fonts/title.ttf";
import fire from "./icons/fire.png";
import grass from "./icons/grass.png";
import water from "./icons/water.png";
import { actions, properties } from "./listings.contract";

export default defineScreen({
  id: "gts:listings",
  properties,
  actions,
  assets: { fire, water, grass },
  render: (p, a) => (
    <column gap={4} padding={8}>
      <text value="GTS" font={titleFont} />

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
