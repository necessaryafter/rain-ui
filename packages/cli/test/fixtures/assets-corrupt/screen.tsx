import { defineActions, defineProperties, defineScreen } from "@rain-ui/core";

import picture from "./images/broken.png";

export default defineScreen({
  id: "test:assets-corrupt",
  properties: defineProperties({}),
  actions: defineActions({}),
  render: () => <image src={picture} />,
});
