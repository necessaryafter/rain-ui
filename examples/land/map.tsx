import { defineScreen } from "@rain-ui/core";
import { actions, properties } from "./map.contract";

export default defineScreen({
  id: "land:map",
  properties,
  actions,
  render: (p, a) => (
    <column gap={4} padding={8}>
      <text value={p.center} />

      <list source={p.rows}>
        {(row) => (
          <row gap={1}>
            <list source={row.cells}>
              {(cell) => (
                <show
                  when={cell.canClaim}
                  fallback={<text value={cell.label} color={cell.color} />}
                >
                  <button action={a["land:claim"]} payload={{ x: cell.x, z: cell.z }}>
                    <text value={cell.label} color={cell.color} />
                  </button>
                </show>
              )}
            </list>
          </row>
        )}
      </list>

      <row gap={2}>
        <button action={a["land:move"]} payload={{ dx: -1, dz: 0 }}>
          <text value="<" />
        </button>
        <button action={a["land:move"]} payload={{ dx: 0, dz: -1 }}>
          <text value="^" />
        </button>
        <button action={a["land:move"]} payload={{ dx: 0, dz: 1 }}>
          <text value="v" />
        </button>
        <button action={a["land:move"]} payload={{ dx: 1, dz: 0 }}>
          <text value=">" />
        </button>
      </row>
    </column>
  ),
});
