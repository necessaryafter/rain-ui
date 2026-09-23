import { defineScreen } from "@rain-ui/core";
import { actions, properties } from "./permissions.contract";

export default defineScreen({
  id: "land:permissions",
  properties,
  actions,
  render: (p, a) => (
    <column gap={6} padding={8}>
      <text value={p.title} />
      <show when={p.isChunkOverride}>
        <text value="Este chunk tem permissões próprias" color="#FFAA00" />
      </show>

      <list source={p.permissions}>
        {(permission) => (
          <row gap={4}>
            <text value={permission.actionLabel} />

            <button action={a["land:cycle-scope"]} payload={{ action: permission.action }}>
              <match value={permission.scope}>
                <case is="PUBLIC">
                  <text value={permission.scopeLabel} color="#FF5555" />
                </case>
                <case is="CITY">
                  <text value={permission.scopeLabel} color="#FFAA00" />
                </case>
                <case is="TRUSTED">
                  <text value={permission.scopeLabel} color="#55FF55" />
                </case>
              </match>
            </button>

            <show when={permission.isDefault} fallback={
              <button action={a["land:reset-scope"]} payload={{ action: permission.action }}>
                <text value="Restaurar padrão" />
              </button>
            }>
              <text value="(padrão)" color="#AAAAAA" />
            </show>
          </row>
        )}
      </list>

      <text value="Jogadores confiados" />
      <show when={p.trusted} fallback={<text value="Ninguém" color="#AAAAAA" />}>
        <list source={p.trusted}>
          {(player) => (
            <row gap={4}>
              <text value={player.playerName} />
              <button action={a["land:untrust"]} payload={{ playerId: player.playerId }}>
                <text value="Remover" />
              </button>
            </row>
          )}
        </list>
      </show>
    </column>
  ),
});
