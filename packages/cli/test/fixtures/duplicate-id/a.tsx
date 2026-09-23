import { defineActions, defineProperties, defineScreen } from "@rain-ui/core";

export default defineScreen({
  id: "test:dup",
  properties: defineProperties({}),
  actions: defineActions({}),
  render: () => <text value="a" />,
});
