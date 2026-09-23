import { defineActions, defineProperties, defineScreen } from "@rain-ui/core";

export default defineScreen({
  id: "test:invalid",
  properties: defineProperties({}),
  actions: defineActions({}),
  render: () => (
    <button action="test:missing">
      <text value="Click" />
    </button>
  ),
});
