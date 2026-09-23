import { defineActions, defineProperties, defineScreen } from "@rain-ui/core";

export default defineScreen({
  id: "test:nondeterministic",
  properties: defineProperties({}),
  actions: defineActions({}),
  render: () => <text value={String(Math.random())} />,
});
