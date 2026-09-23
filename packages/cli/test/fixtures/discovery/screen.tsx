import { defineScreen } from "@rain-ui/core";
import Title from "./component";
import { actions, properties } from "./contract";
import { greeting } from "./util";

export default defineScreen({
  id: "test:discovery",
  properties,
  actions,
  render: (p) => (
    <column>
      <text value={greeting()} />
      <Title value={p.title} />
    </column>
  ),
});
