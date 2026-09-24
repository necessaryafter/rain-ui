import { defineActions, defineProperties, defineScreen } from "@rain-ui/core";

import picture from "./images/wide.png";

export default defineScreen({
  id: "test:assets-too-wide",
  properties: defineProperties({}),
  actions: defineActions({}),
  render: () => <image src={picture} />,
});
