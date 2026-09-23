import { defineActions, defineProperties, defineScreen } from "@rain-ui/core";
import { label } from "fake-random";

export default defineScreen({
  id: "test:node-modules",
  properties: defineProperties({}),
  actions: defineActions({}),
  render: () => <text value={label()} />,
});
