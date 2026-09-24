import { defineActions, defineProperties, defineScreen } from "@rain-ui/core";

import picture from "./images/photo.webp";

export default defineScreen({
  id: "test:assets-unsupported",
  properties: defineProperties({}),
  actions: defineActions({}),
  render: () => <image src={picture} />,
});
