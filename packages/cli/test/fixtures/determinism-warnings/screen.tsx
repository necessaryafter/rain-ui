import { defineActions, defineProperties, defineScreen } from "@rain-ui/core";
import { Random } from "./components/Random";
import { builtAt, builtYear } from "./helpers/time";

export default defineScreen({
  id: "test:warnings",
  properties: defineProperties({}),
  actions: defineActions({}),
  render: () => (
    <column>
      <Random />
      <text value={builtAt()} />
      <text value={builtYear()} />
    </column>
  ),
});
