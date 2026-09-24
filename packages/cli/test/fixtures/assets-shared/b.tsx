import { defineActions, defineProperties, defineScreen } from "@rain-ui/core";

import banner from "./banner.png";
import bannerCopy from "./banner-copy.png";

export default defineScreen({
  id: "test:shared-b",
  properties: defineProperties({}),
  actions: defineActions({}),
  render: () => (
    <column>
      <image src={banner} />
      <image src={bannerCopy} />
    </column>
  ),
});
