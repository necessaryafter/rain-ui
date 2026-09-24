import { defineActions, defineProperties, defineScreen } from "@rain-ui/core";

import banner from "./banner.png";

export default defineScreen({
  id: "test:shared-a",
  properties: defineProperties({}),
  actions: defineActions({}),
  render: () => <image src={banner} />,
});
