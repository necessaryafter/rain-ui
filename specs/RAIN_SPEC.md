# Rain UI — Spec v0

## 1. Visão

Rain UI é um framework open source para substituir menus de baú em servidores de Minecraft por UIs de verdade.

1. O dev descreve a estrutura visual e as interações de uma tela em TSX.
2. Um passo de build executa esse TSX uma única vez e gera um **contrato JSON**.
3. O servidor envia o contrato e os dados da tela para o cliente.
4. O mod no cliente valida tudo, faz o binding dos dados e renderiza.
5. Cliques voltam ao servidor como uma ação com ID e payload, e o servidor decide o que acontece.

Referência de design: os dialogs do Minecraft (1.21.6+). Mesmo modelo de protocolo (definição como dado, ações com ID
namespaced + payload, cliente sem regra de negócio), mas com layout e componentes de verdade.

## 2. Premissas

| Item                                 | Valor                                                                                         |
|--------------------------------------|-----------------------------------------------------------------------------------------------|
| Versões do Minecraft                 | 1.21.1 e 26.3, suportadas em paralelo                                                         |
| Java                                 | 21 para 1.21.1 e 25 para 26.3; módulos compartilhados compilados com `--release 21`           |
| Mappings                             | Yarn para 1.21.1 e oficiais para 26.3                                                         |
| Loader                               | Fabric, lado cliente e servidor                                                               |
| Plataforma de servidor no v0         | Fabric; o core de servidor não depende de plataforma, para permitir um adaptador Paper depois |
| Build Java                           | Gradle (Kotlin DSL) + Fabric Loom                                                             |
| Runtime TS                           | Bun                                                                                           |
| JSON no Java                         | Jackson                                                                                       |
| Group ID / pacote base               | `com.rainframework.ui`                                                                        |
| Idioma de código, comentários e docs | Inglês                                                                                        |

## 3. Decisões fechadas

Não reabrir nenhuma destas sem perguntar.

1. **O JS nunca roda no cliente.** O TSX é executado só no build. O mod não contém motor JS.
2. **O TSX define só estrutura visual e interação.** Não há regra de negócio nem expressões no contrato. Tudo que é
   dinâmico é binding para uma propriedade declarada. Condições (ex.: botão desabilitado) e formatação (ex.: preço como
   texto) são calculadas pelo servidor e enviadas como campos.
3. **Contrato e propriedades são entrada não confiável no cliente.** Interações são entrada não confiável no servidor.
4. **Paridade com o vanilla.** O Rain nunca dá ao servidor um poder que ele não tenha no vanilla. A única IO de rede
   que o cliente faz a pedido do servidor é **baixar conteúdo declarado por hash** (contratos e assets), com
   consentimento do jogador, no mesmo molde do resource pack do vanilla (URL escolhida pelo servidor, hash, prompt).
   O cliente não baixa URL arbitrária, não acessa arquivos fora do próprio cache, não lê clipboard e não recebe input
   fora de uma tela Rain focada. ESC sempre fecha a tela. Ver `docs/decisions.md` §8.
5. **Toda ação vai para o servidor.** Não existem props que abrem link, rodam comando ou copiam texto no cliente.
   Comandos são executados pelo servidor.
6. **Sem reflection na serialização.** Objetos viram JSON apenas por adapters explícitos registrados por tipo. Só vai
   para o cliente o que o adapter escreve.
7. **O jogador vem da conexão**, nunca de um campo do pacote.
8. **IDs de tela e de ação** usam o formato de identificador do Minecraft (`namespace:path`), são case-sensitive e nunca
   chegam nulos ao código do usuário.

## 4. Arquitetura

### 4.1 Fluxo

1. O dev escreve a tela (ex.: `main.tsx`, com as declarações em `main.contract.ts`) e importa os assets que ela usa
   (`import banner from "./banner.png"`).
2. `rain build` executa as telas com Bun, valida contra o schema e grava `dist/<namespace>/<tela>.json`, os assets em
   `dist/assets/<sha256>` e o manifesto com os hashes.
3. O conteúdo estático é hospedado para download: pelo servidor HTTP base do Rain ou por qualquer servidor de arquivos
   estáticos (nginx, CDN, o framework do dev). O servidor do jogo carrega os contratos no startup.
4. No join, o `Hello` do servidor informa o `assetBaseUrl`.
5. `RainScreens.open(player, "shop:main", properties)` cria uma instância de tela e envia `OpenScreen` com o hash do
   contrato e os dados.
6. Se o cliente não tem o contrato ou algum asset em cache, pede consentimento (uma vez por servidor), baixa por
   `GET <assetBaseUrl>/<sha256>` e confere o hash de cada arquivo.
7. O mod valida contrato e propriedades, faz o binding e renderiza.
8. Um clique envia `Interact(instanceId, revision, actionId, payload)`.
9. O servidor valida, despacha o evento e envia exatamente uma resposta.

Por HTTP vai só o que é estático, igual para todos e endereçado por hash. Tudo que é por jogador (properties, cliques,
respostas) continua na conexão do jogo, que já tem ordem e autenticação.

### 4.2 Estrutura do repositório

```
rain-ui/
├── schema/
│   ├── contract.v0.schema.json
│   └── fixtures/
│       ├── valid/              # compartilhadas entre os testes TS e Java
│       └── invalid/            # cada arquivo declara o código de erro esperado
├── packages/
│   ├── core/                   # @rain-ui/core: jsx-runtime, tipos dos componentes, builder t.*
│   └── cli/                    # rain build, rain dev
├── examples/
│   └── shop/
│       ├── main.tsx            # tela
│       ├── main.contract.ts    # declarações de properties e actions
│       └── components/         # componentes reutilizáveis importados pela tela
├── java/
│   ├── rain-protocol/          # Java puro: modelo do contrato, parser, validador, codecs dos pacotes, limites
│   ├── rain-server/            # Java puro: RainScreens, Properties, adapters, dispatcher, eventos, servidor HTTP base
│   ├── rain-client-core/       # Java puro: árvore de componentes, layout, binding, estado pendente, download e cache de assets
│   ├── rain-fabric-1.21.1/     # adaptadores Fabric para 1.21.1 (Yarn, Java 21)
│   └── rain-fabric-26.3/       # adaptadores Fabric para 26.3 (mappings oficiais, Java 25)
├── docs/
│   ├── SPEC.md
│   └── decisions.md
└── CLAUDE.md
```

O `rain build` gera `dist/` com `manifest.json`, `<namespace>/<tela>.json` e `assets/<sha256>` (arquivos planos, um
por hash, prontos para qualquer servidor de arquivos estáticos).

`rain-protocol`, `rain-server` e `rain-client-core` não importam nenhuma classe do Minecraft. Tudo que depende de
plataforma entra por interfaces implementadas nos módulos de versão.

### 4.3 Multi-versão

- **Módulos compartilhados.** Os três módulos Java puros são os mesmos para as duas versões e são compilados com
  `--release 21`, para rodar em ambas. Neles não entra nenhuma API do Java 22+.
- **Módulos de versão.** Cada `rain-fabric-<versão>` é uma camada fina de adaptadores que implementa as interfaces de
  plataforma:
    - networking: registro e envio dos custom payloads;
    - renderização: desenhar retângulo, texto, item e imagem, e medir texto;
    - assets: decodificar imagem (PNG, JPEG, GIF) e fonte (TTF, OTF) com o que o cliente já traz (`lwjgl-stb`,
      `lwjgl-freetype`);
    - input: traduzir mouse e teclado da tela para eventos do core;
    - ItemStack: codificar no servidor e decodificar no cliente com o codec vanilla da versão;
    - servidor: jogador, thread principal e ciclo de vida da conexão.
- **Regra de posicionamento.** Se um trecho não precisa de classe do Minecraft, ele vai para um módulo compartilhado.
  Lógica duplicada entre os dois módulos de versão é sinal de que ela deveria estar no core.
- **Protocolo e contrato idênticos nas duas versões.** Mesmo `protocolVersion`, mesmos codecs, mesmo JSON e mesmo hash
  de contrato. Só os dados que carregam ItemStack dependem da versão, e cliente e servidor estão sempre na mesma versão.
- **Limite de pacote por versão.** O limite de tamanho de custom payload pode diferir entre as versões. Cada adaptador
  expõe o limite da sua versão (confirmado nas fontes), e os limites de properties e payload precisam caber nele.

## 5. Contrato v0

### 5.1 Declarações

Toda tela declara:

- `id`: identificador namespaced da tela.
- `properties`: schema das propriedades que o servidor envia.
- `actions`: as ações que a tela pode disparar e o schema do payload de cada uma.

No TS, as declarações são feitas com um builder de runtime `t.*` (estilo zod): os tipos TS são inferidos dele e ele é
emitido no contrato. A API exata deve ser proposta no plano do M1.

- **Declarações em módulo separado.** As declarações podem ficar num arquivo próprio (ex.: `main.contract.ts`) e ser
  importadas pela tela. O build resolve os imports normalmente, e a saída é um único JSON, sem nenhuma referência a
  arquivos.
- **Sem alargar o tipo.** As exports de declaração não podem ter o tipo alargado por anotação (ex.:
  `Record<string, Schema>`), porque isso apaga a tipagem dos bindings dentro do `render`. O core oferece
  `defineProperties` e `defineActions`: funções identidade que checam o formato sem perder a inferência.

Tipos do v0: `string`, `int`, `long`, `double`, `bool`, `item` (ItemStack), `asset`, `list(T)`, `object({...})`.

- **Opcional.** `t.string().optional()` aceita valor ausente ou `null`, e os dois significam "sem valor". No contrato:
  `{ "kind": "string", "optional": true }`. Vale para qualquer tipo.
- **Padrão.** `t.string().default("Sem descrição")` só existe em `string`, `int`, `long`, `bool` e `double`, e já
  torna o campo opcional. O cliente usa o padrão quando o valor está ausente ou é `null` (não quando é `""`). Só pode
  aparecer em `properties`, nunca no schema de uma ação. No contrato: `{ "kind": "string", "default": "…" }`.
- **Decimal.** `double` serve para dados e payload. Para aparecer na tela, o servidor manda um label formatado.
- **Asset escolhido pelo servidor.** `t.asset()` recebe o **nome** de um asset declarado em
  `defineScreen({ assets: { fire, water } })`, nunca um hash nem uma URL. Ver §5.3.
- As properties são o **view model** da tela: o servidor manda tudo pronto para exibir. Ver `docs/properties.md`.

### 5.2 Componentes do v0

| Componente      | Props principais                                                                          | Filhos           |
|-----------------|-------------------------------------------------------------------------------------------|------------------|
| `column`, `row` | `gap`, `padding`, `align`, `justify`, `width`, `height`                                   | sim              |
| `text`          | `value` (string, ou binding de `string`/`int`/`long`), `color`, `font`, `align`, `shadow` | não              |
| `image`         | `src` (asset de imagem, ou binding de `asset`), `width`, `height`                         | não              |
| `item`          | `value` (binding de `item`), `size`                                                       | não              |
| `button`        | `action`, `payload`, `disabled` (bool ou binding de bool)                                 | sim              |
| `list`          | `source` (binding de `list`); o filho é um template com o item no escopo                  | sim              |
| `show`          | `when` (binding de qualquer tipo), `fallback` (sub-árvore)                                | sim              |
| `match`         | `value` (binding de `string`/`int`/`long`/`bool`)                                         | `case`/`default` |
| `case`          | `is` (literal do tipo do `value` do `match`)                                              | sim              |
| `default`       | —                                                                                         | sim              |

Tamanhos em pixels de GUI, respeitando a GUI scale. `width`/`height` aceitam número, `fit` ou `fill`.

- **`color`** aceita literal `#RRGGBB` ou binding de string. Se o valor recebido não for `#RRGGBB`, o cliente usa a cor
  padrão, sem erro.
- **`text.value` com número** mostra o decimal cru (`1250`), sem separador e sem depender do idioma. `double` e `bool`
  não são aceitos: o servidor manda um label.
- **`show`** desenha os filhos quando `when` tem valor, e o `fallback` quando não tem. Para `bool`, vale o próprio
  valor. Para os outros tipos, ausente, `null`, `""` e `[]` contam como sem valor. No TSX, `fallback` é uma prop; no
  contrato, vira um último filho do tipo `fallback`.
- **`match`** desenha o primeiro `case` cujo `is` é igual ao valor, ou o `default` (o último filho, e opcional) quando
  nenhum bate ou o valor está ausente. `case` e `default` só existem dentro de `match`, e `fallback` só dentro de
  `show`.

- **`text.font`** aceita um asset de fonte.
- **`image`** desenha um PNG, JPEG ou GIF (animado). Sem `width`/`height`, usa as dimensões declaradas no contrato.

No M3: `grid`, `input` e `scroll`.

### 5.3 Assets

Imagens e fontes são importadas no TSX e viram entradas do contrato:

```json
"assets": {
  "<sha256>": { "type": "image/png", "bytes": 48213, "width": 128, "height": 64 },
  "<sha256>": { "type": "image/gif", "bytes": 90112, "width": 32, "height": 32, "frames": 12 },
  "<sha256>": { "type": "font/ttf", "bytes": 21504 }
},
"assetNames": { "fire": "<sha256>", "water": "<sha256>" }
```

- Um componente referencia um asset com `{ "$asset": "<sha256>" }`. O hash precisa estar em `assets`, com um tipo
  aceito pela prop.
- `assetNames` mapeia os nomes que o servidor pode mandar numa property `t.asset()`. O cliente só baixa o que está no
  contrato, e o contrato é verificado pelo hash que veio pela conexão do jogo. Então **um único hash valida todo o
  conteúdo** da tela.
- Formatos do v0: `image/png`, `image/jpeg`, `image/gif`, `font/ttf` e `font/otf`. Vídeo e WebP ficam para depois do
  v0, como mais tipos nesta mesma tabela.
- Imagens declaram `width` e `height` (e `frames` no GIF), para o layout reservar espaço antes do download e o cliente
  recusar antes de baixar. Depois do download, o cliente confere o cabeçalho real contra o declarado.

### 5.4 Bindings

- Binding é só um caminho, sem expressões. Dentro de `list`, o escopo inclui o item atual.
- O caminho atravessa objetos com ponto: `owner.name`, `bidData.currentBid`. Um objeto inteiro também pode ser
  ligado, onde a prop aceitar (ex.: `show when`).
- Um binding pode apontar para uma propriedade opcional. Sem valor, cada prop usa o seu padrão: `text.value` → `""`,
  `item.value` → nada, `disabled` → `false`, `list.source` → `[]`, `color` → cor padrão.
- No `render`, um binding é um marcador e não o valor. Usar um binding como valor JS (template string, concatenação,
  comparação, `.length`, `.map`) é erro de build. O build avisa sobre property declarada e nunca usada.
- Todo binding precisa resolver para uma propriedade declarada com tipo compatível com a prop: `text.value` exige
  string, `item.value` exige item, `image.src` exige asset, `list.source` exige list, `disabled` exige bool.
- Valores do `payload` são literais ou bindings para campos em escopo. Um campo opcional da ação pode faltar. Um campo
  obrigatório não aceita binding de propriedade opcional sem padrão.
- O formato exato do binding no JSON deve ser proposto no plano do M1.

### 5.5 Exemplo (ilustrativo, a API final sai do plano)

```ts
// examples/shop/main.contract.ts
import { defineActions, defineProperties, t } from "@rain-ui/core";

export const properties = defineProperties({
  items: t.list(t.object({
    id: t.string(),
    name: t.string(),
    priceLabel: t.string(),
    icon: t.item(),
    locked: t.bool(),
  })),
});

export const actions = defineActions({
  "shop:buy": t.object({ itemId: t.string() }),
});
```

```tsx
// examples/shop/main.tsx
import { defineScreen } from "@rain-ui/core";
import { actions, properties } from "./main.contract";

export default defineScreen({
  id: "shop:main",
  properties,
  actions,

  render: (p) => (
    <column gap={4} padding={8}>
      <text value="Loja" />

      <list source={p.items}>
        {(item) => (
          <row gap={4}>
            <item value={item.icon} />
            <text value={item.name} />
            <text value={item.priceLabel} />

            <button
              action="shop:buy"
              payload={{ itemId: item.id }}
              disabled={item.locked}
            >
              <text value="Comprar" />
            </button>
          </row>
        )}
      </list>
    </column>
  ),
});
```

### 5.6 Validação

As mesmas regras rodam no `rain build` (TS) e no mod (Java). As fixtures em `schema/fixtures` são a fonte da verdade
para os dois lados.

- `schemaVersion` conhecido; versão desconhecida é recusada.
- Só componentes e props conhecidos, com tipos corretos. Qualquer coisa desconhecida é erro, sem tentativa de renderizar
  parcialmente.
- Bindings resolvem para propriedades declaradas com tipo compatível.
- Toda ação usada num `button` está declarada, e o `payload` bate com o schema declarado dela.
- IDs no formato válido.
- Limites (valores iniciais, como constantes em `rain-protocol`):
    - contrato: 256 KiB serializado, 2000 nós, profundidade 32, 256 ações, strings de até 1024 caracteres;
    - propriedades por envio: 256 KiB, listas de até 1000 elementos;
    - propriedades: strings de até 4096 caracteres;
    - payload de interação: 8 KiB e profundidade 8;
    - assets: 8 MiB cada, 256 e 64 MiB no total por contrato, imagens de até 4096 px por lado, GIF de até 512 frames
      e 128 MiB decodificados;
    - IDs de tela e de ação nos pacotes: 256 bytes;
    - profundidade máxima de qualquer JSON: 128, checada no parser streaming com contador próprio (não depender do
      limite de aninhamento da biblioteca de JSON). Ver `docs/decisions.md` §3.
- Erros têm um código estável (`UNKNOWN_COMPONENT`, `UNDECLARED_BINDING`, `LIMIT_EXCEEDED`, `INVALID_DEFAULT`,
  `MISPLACED_COMPONENT`, `DUPLICATE_CASE`, ...) e o caminho do nó (`root.children[2].props.value`) ou da declaração
  (`properties.listings.of.fields.price`). Os testes checam o código, nunca o texto da mensagem.

## 6. Protocolo

O protocolo tem duas partes: **pacotes** na conexão do jogo, para tudo que é por jogador, e **download HTTP**, só para
conteúdo estático endereçado por hash. Todos os codecs dos pacotes ficam em `rain-protocol`, em Java puro. Os módulos de
versão só embrulham esses codecs nos custom payloads.

### Download de conteúdo

```
GET <assetBaseUrl>/<sha256 em hex minúsculo>   →   os bytes do arquivo
```

- Serve contratos e assets igualmente. Qualquer servidor de arquivos estáticos atende: o servidor HTTP base do Rain
  (JDK `HttpServer`, em `rain-server`) ou o que o dev escolher, bastando publicar `dist/assets/` e os contratos.
- HTTP e HTTPS são aceitos: a integridade vem do hash, não do transporte.
- O cliente não segue redirect para outro host, não envia cookies nem credenciais, usa timeout e limita o tamanho pelo
  declarado no contrato.

### Servidor → cliente

| Pacote                | Campos                                                                 |
|-----------------------|------------------------------------------------------------------------|
| `Hello`               | `protocolVersion`, `assetBaseUrl`                                      |
| `OpenScreen`          | `instanceId`, `screenId`, `contractHash`, `revision`, `propertiesJson` |
| `UpdateScreen`        | `instanceId`, `revision`, `propertiesJson`                             |
| `InteractionRejected` | `instanceId`, `revision`                                               |
| `CloseScreen`         | `instanceId`                                                           |

### Cliente → servidor

| Pacote         | Campos                                              |
|----------------|-----------------------------------------------------|
| `Hello`        | `protocolVersion`                                   |
| `Interact`     | `instanceId`, `revision`, `actionId`, `payloadJson` |
| `ScreenClosed` | `instanceId`                                        |
| `ScreenFailed` | `instanceId`, `reason`                              |

### Encoding

- Cada pacote é um custom payload próprio, com ID `rain:<nome>` (`rain:server_hello`, `rain:open_screen`,
  `rain:update_screen`, `rain:interaction_rejected`, `rain:close_screen`, `rain:client_hello`, `rain:interact`,
  `rain:screen_closed`, `rain:screen_failed`). Os bytes não levam cabeçalho de tipo.
- Inteiros são VarInt sem sinal (o LEB128 do Minecraft, até 5 bytes, só valores não negativos). Strings e JSON levam o
  tamanho em bytes como VarInt e depois o UTF-8, que precisa ser válido. O hash vai como 32 bytes crus.
- O leitor checa cada tamanho contra o limite do campo e contra o que resta no pacote **antes de alocar**, e recusa
  bytes sobrando no fim.
- `protocolVersion` começa em `1`.

### Regras

- **Handshake no join.** Se a versão do protocolo for incompatível, o servidor não abre telas Rain para esse jogador e
  loga. Kick com mensagem fica como opção de configuração.
- **Hash.** O cliente calcula o SHA-256 de todo arquivo baixado antes de usar. Se não bater, descarta.
- **JSON canônico.** O build grava o contrato com chaves ordenadas, para o hash ser estável entre builds idênticos.
- **`ScreenFailed`.** Enviado quando a tela não pode abrir (download falhou, consentimento recusado, contrato ou
  properties inválidos), para o servidor liberar a instância. `reason` é um código curto, só para log.
- **Cache.** Em disco, por hash, compartilhado entre servidores, com limite total (512 MiB por padrão, configurável no
  cliente) e remoção por LRU.
- **`instanceId`.** Gerado pelo servidor e nunca reutilizado na mesma sessão.
- **Item nas properties.** Um valor `item` é o base64 dos bytes do codec de rede de ItemStack do vanilla, opaco para o
  protocolo. Os IDs de registro dentro dele valem só para aquela conexão, então properties nunca são cacheadas.

## 7. Servidor

### 7.1 API pública

```java
Rain.registerAdapter(ShopItem.class, (item, out) -> out
    .put("id", item.getId())
    .put("name", item.getDisplayName())
    .put("priceLabel", item.getPriceLabel())
    .putItem("icon", item.getIcon())
    .put("locked", !item.isAvailable()));

RainScreens.open(player, "shop:main", Properties.builder()
    .add("items", shop.items())
    .build());

RainScreenEvents.INTERACTION_RECEIVED.subscribe(event -> {
    if (!event.getId().equals("shop:buy")) {
        return;
    }

    final String itemId = event.getPayload().getString("itemId");
    final ShopItem item = shop.find(itemId); // sempre resolvido no servidor
    if (item == null || !economy.withdraw(event.getPlayer(), item.getPrice())) {
        event.setResult(InteractionResult.reject());
        return;
    }

    event.setResult(InteractionResult.update(/* novas properties */));
});
```

- `Properties` é validado contra as propriedades declaradas no contrato **no momento do `open`**, ainda no servidor. Uma
  chave não declarada ou de tipo errado lança exceção imediatamente.
- `putItem` delega a codificação para o módulo de versão, que usa o codec vanilla de ItemStack daquela versão. O mod
  decodifica com o mesmo codec.
- Os acessores do payload (`getString`, `getInt`, `getLong`, `getBool`, ...) lançam exceção tipada se o tipo não bater.
  Nunca convertem. Para campo opcional, `findString`, `findInt`, ... devolvem `null` quando não há valor, anotados com
  `@Nullable` (JSpecify), para o Kotlin enxergar `String?`.

### 7.2 Dispatcher

Antes de disparar o evento, nesta ordem (cada falha rejeita e loga em nível debug):

1. Obter o jogador pela conexão.
2. Aplicar rate limit por jogador (valor inicial: 10 interações/s).
3. Conferir que o `instanceId` existe, pertence a esse jogador e está aberto.
4. Conferir que `revision` é a atual. Se for diferente, rejeitar e reenviar `UpdateScreen` com o estado atual.
5. Conferir que o `actionId` está declarado no contrato dessa tela.
6. Validar o payload: tamanho, profundidade e schema declarado da ação.
7. Passar para a thread do servidor antes de disparar o evento.

Depois do evento:

- Sempre sai exatamente uma resposta. Se nenhum subscriber definiu resultado, o padrão é `reject()`.
- `update` incrementa a revision. Com isso, num duplo clique o segundo pacote chega com revision velha e é rejeitado.
- No v0 os handlers são síncronos. Handlers assíncronos ficam fora do escopo.

## 8. Cliente (mod)

A lógica do cliente fica em `rain-client-core`. Os módulos de versão só desenham e traduzem input.

- Tela custom. Input chega à tela Rain apenas enquanto ela está focada. HUD fora do escopo.
- ESC sempre fecha a tela e envia `ScreenClosed`.
- Engine de layout própria e simples (row/column com gap, padding, align, justify e tamanhos fixo/fit/fill), testável
  sem Minecraft.
- `item` é desenhado com o renderer de item vanilla (glint, modelo, contagem).
- **Consentimento.** Na primeira vez que um servidor pede download, o jogador vê o host e o tamanho e escolhe permitir
  ou recusar. A escolha fica lembrada por servidor.
- **Assets.** Download só dos hashes do contrato, verificação do hash, cache em disco, e decodificação só depois de
  conferir no cabeçalho que dimensões, frames e tamanho estão dentro do declarado e dos limites.
- Ao clicar num botão, ele entra em estado pendente (visual) e ignora novos cliques até chegar `UpdateScreen`,
  `InteractionRejected` ou `CloseScreen`, ou até um timeout de 5s.
- Contrato ou propriedades inválidos: a tela não abre, o jogador vê um erro genérico e os detalhes vão para o log.
- Nenhum acesso a arquivo fora do próprio cache, clipboard, URL fora do `assetBaseUrl` ou comando a pedido do
  servidor.

## 9. TypeScript

- `tsconfig`: `"jsx": "react-jsx"` e `"jsxImportSource": "@rain-ui/core"`.
- O jsx-runtime devolve objetos comuns. Componentes funcionais são chamados na hora.
- `JSX.IntrinsicElements` tipado com as props de cada componente, aceitando literal ou binding do tipo certo (ex.:
  `text.value` aceita `string | Binding<string>`).
- **Descoberta de telas.** `rain build <dir>` importa os módulos `.ts`/`.tsx` do diretório e só trata como tela o módulo
  cujo default export é resultado de `defineScreen` (marcado com um símbolo interno). Contratos, componentes e
  utilitários são ignorados como entrada. Dois módulos com o mesmo `id` de tela são erro de build.
- **Saída.** Para cada tela: executa, valida contra o schema e grava o JSON canônico, mais o manifesto com os hashes.
  Falhas mostram código de erro e caminho do nó.
- **Determinismo.**
    - O build avisa sobre `Date.now`, `new Date()` e `Math.random` em todos os módulos locais do grafo de imports de
      cada tela, e não só no arquivo da tela, porque o valor congela na hora do build. Dependências de `node_modules`
      ficam de fora do aviso.
    - `rain build --check` executa o build duas vezes, em processos separados, e falha se algum hash diferir. Pensado
      para rodar no CI.
- **Ações tipadas (decidir no plano do M1).** Com `action="shop:buy"` como string, um ID errado só é pego no build,
  porque os tipos dos elementos JSX minúsculos são globais e não conhecem as ações da tela. Para o editor acusar o erro,
  o `render` pode receber as ações tipadas (`render: (p, a) => ... action={a["shop:buy"]}`), com um botão genérico que
  amarra o tipo do payload à ação. O plano do M1 deve propor a abordagem.
- `rain dev` (watch + hot reload) fica para o M3.

## 10. Modelo de ameaça (base do futuro `THREAT_MODEL.md`)

**Atacantes considerados:**

- servidor malicioso, contra o jogador;
- cliente modificado, contra o servidor;
- rede, entre os dois;
- host de assets malicioso ou comprometido, contra o jogador.

**Invariantes:** as decisões da seção 3.

**Download de conteúdo:**

- o hash do contrato vem pela conexão do jogo, e o contrato fixa o hash de cada asset. Um host comprometido não
  consegue trocar conteúdo, só deixar de servir;
- a decodificação de imagem e fonte é a parte de maior risco: o cliente usa os decoders que o Minecraft já traz e só
  decodifica depois de checar no cabeçalho dimensões, frames e tamanho;
- o host fica sabendo o IP do jogador, como já acontece com o resource pack. Por isso existe o consentimento, que mostra
  o host;
- sem redirect para outro host, sem cookies, sem credenciais, com timeout e com tamanho máximo pelo declarado.

**Fora do escopo do v0:** containers com item real (mover itens), HUD, links, vídeo, handlers assíncronos, adaptador
Paper.

## 11. Marcos

### M0 — Esqueleto

Monorepo com os cinco módulos Java e os pacotes TS, `CLAUDE.md` com os comandos do projeto e `docs/decisions.md` vazio.

**Pronto quando:**

- `./gradlew build` e `bun test` passam;
- os módulos compartilhados compilam com `--release 21`;
- `runClient` e `runServer` sobem com o mod carregado nas duas versões (1.21.1 e 26.3).

### M1 — Contrato, sem Minecraft

Schema v0, fixtures válidas e inválidas, `@rain-ui/core`, `rain build`, e modelo + parser + validador em
`rain-protocol`.

**Pronto quando:**

- `rain build examples/shop` gera o JSON;
- o exemplo com contrato em arquivo separado e componente importado gera exatamente o mesmo JSON que a versão com tudo
  no mesmo arquivo;
- módulos sem `defineScreen` como default export são ignorados, e `id` de tela duplicado falha o build;
- um `Math.random()` dentro de um componente importado gera o aviso;
- `rain build --check` passa no exemplo e falha numa tela de teste não determinística;
- toda fixture válida passa e toda inválida falha com o código esperado, nos dois lados (TS e Java);
- cada limite é testado na borda: o valor N passa e N+1 falha.

### M1.5 — Assets no build

Imports de asset no TSX, `t.asset()`, `defineScreen({ assets })`, `image`, `text.font`, e a tabela de assets validada
nos dois lados.

**Pronto quando:**

- `rain build` grava `dist/assets/<sha256>` com os bytes originais e lista os assets no manifesto;
- um mesmo arquivo usado por duas telas vira um asset só;
- asset corrompido, de formato não suportado ou acima dos limites falha o build com código e caminho;
- as fixtures de assets passam nos dois lados (TS e Java).

### M2 — Ponta a ponta

Pacotes, download de conteúdo, `rain-server` (com o servidor HTTP base), `rain-client-core` e os dois módulos de
versão, com todos os componentes do v0.

**Pronto quando** (testes unitários, testes do dispatcher e teste manual nas duas versões):

- o layout é testado em `rain-client-core` sem Minecraft (posições e tamanhos calculados para árvores de exemplo);
- a loja abre com os itens enviados pelo servidor;
- a compra funciona;
- revision velha é rejeitada e o estado atual é reenviado;
- duplo clique gera uma compra só;
- handler sem resultado tira o cliente do estado pendente;
- interação com `instanceId` de outro jogador é rejeitada;
- payload fora do schema é rejeitado antes de chegar ao evento;
- `Properties` com chave não declarada falha no `open`, no servidor;
- o contrato e os assets baixam do servidor HTTP base e de um servidor estático externo;
- arquivo com hash errado é descartado e a tela não abre, e o servidor recebe `ScreenFailed`;
- consentimento recusado não baixa nada;
- imagem acima dos limites é recusada antes de decodificar.

### M3 — Expansão

- `grid` e `scroll`;
- `input`, com os valores entrando no payload via binding;
- `rain dev` com hot reload.

### Depois do v0

- Vídeo e WebP, como novos tipos de asset.

## 12. Como trabalhar

1. **Um marco por vez.** Comece cada marco em Plan mode, proponha o plano e espere aprovação.
2. **Testes primeiro.** A primeira entrega de cada marco são só os testes e as fixtures, derivados dos critérios desta
   spec. Pare e espere revisão.
3. **Testes congelados.** Depois que os testes forem aprovados e commitados, não edite, apague, renomeie nem desative
   nenhum teste ou fixture (nada de `@Disabled` ou `.skip`). Se um teste parecer errado, pare e explique o motivo; a
   decisão não é sua.
4. **Não invente decisões.** Se algo não está nesta spec, pergunte. Decisões aprovadas vão para `docs/decisions.md`.
5. **Confirme APIs nas fontes.** APIs de Minecraft e Fabric devem ser checadas nas fontes de cada versão alvo (as
   sources geradas pelo Loom), não assumidas de memória. Vale principalmente para networking, codec de ItemStack,
   renderização de GUI e limites de tamanho de pacote.
6. **Sem reflection para serialização e sem dependências novas** sem perguntar antes.
7. **Isolamento de módulos.** `rain-protocol`, `rain-server` e `rain-client-core` não podem importar classes do
   Minecraft.
8. **Duas versões sempre.** Nos módulos compartilhados, só APIs disponíveis no Java 21. Toda mudança num módulo de
   versão precisa ter o equivalente avaliado no outro.

