import { defineScreen } from "@rain-ui/core";
import { actions, properties } from "./overview.contract";

export default defineScreen({
  id: "city:overview",
  properties,
  actions,
  render: (p, a) => (
    <column gap={6} padding={8}>
      <row gap={4}>
        <item value={p.banner} />
        <column gap={2}>
          <text value={p.name} />
          <text value={p.mayorName} color="#AAAAAA" />
        </column>
      </row>

      <row gap={4}>
        <text value="Nível" />
        <text value={p.developmentLevel} />
      </row>

      <column gap={2}>
        <text value={p.government.culture} />
        <text value={p.government.religion} />
        <show when={p.government.governmentName}>
          <text value={p.government.governmentName} />
        </show>
      </column>

      <row gap={4}>
        <text value="Cofre" />
        <text value={p.treasuryLabel} />
      </row>

      <row gap={4}>
        <text value="Imposto" />
        <text value={p.taxRate} />
        <show when={p.canManageTreasury}>
          <row gap={2}>
            <button action={a["city:change-tax"]} payload={{ step: -1 }}>
              <text value="-" />
            </button>
            <button action={a["city:change-tax"]} payload={{ step: 1 }}>
              <text value="+" />
            </button>
          </row>
        </show>
      </row>

      <row gap={4}>
        <match value={p.joinPolicy}>
          <case is="PUBLIC">
            <text value={p.joinPolicyLabel} color="#55FF55" />
          </case>
          <default>
            <text value={p.joinPolicyLabel} color="#FF5555" />
          </default>
        </match>
        <show when={p.canManageCity}>
          <button action={a["city:cycle-join-policy"]} payload={{}}>
            <text value="Alterar" />
          </button>
        </show>
      </row>

      <show when={p.roles} fallback={<text value="Nenhum cargo nomeado" />}>
        <list source={p.roles}>
          {(role) => (
            <row gap={4}>
              <text value={role.typeLabel} />
              <text value={role.playerName} />
            </row>
          )}
        </list>
      </show>

      <show when={p.canManageCity}>
        <show when={p.joinRequests} fallback={<text value="Nenhum pedido de entrada" />}>
          <list source={p.joinRequests}>
            {(request) => (
              <row gap={4}>
                <text value={request.playerName} />
                <button action={a["city:accept-request"]} payload={{ playerId: request.playerId }}>
                  <text value="Aceitar" />
                </button>
                <button action={a["city:deny-request"]} payload={{ playerId: request.playerId }}>
                  <text value="Recusar" />
                </button>
              </row>
            )}
          </list>
        </show>
      </show>
    </column>
  ),
});
