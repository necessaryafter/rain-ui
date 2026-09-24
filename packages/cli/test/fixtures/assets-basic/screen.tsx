import { defineActions, defineProperties, defineScreen } from "@rain-ui/core";

import title from "./fonts/title.ttf";
import banner from "./images/banner.png";
import spinner from "./images/spinner.gif";

export default defineScreen({
  id: "test:assets",
  properties: defineProperties({}),
  actions: defineActions({}),
  render: () => (
    <column gap={4}>
      <image src={banner} />
      <image src={spinner} width={32} height={32} />
      <text value="GTS" font={title} />
    </column>
  ),
});
