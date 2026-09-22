# Code Style

Operational guide for AI agents that write, change, or review code in this repository.

This document describes how code should be written **in this repository**, based primarily on existing code. It is not a generic style guide. When the existing code and this document disagree, prefer the existing code.

## Rule Priority

When patterns conflict, apply this order:

1. Code immediately around the change.
2. The current file and package/module conventions.
3. The dominant convention in the repository.
4. This document.
5. Generic language/framework conventions.

Prefer local consistency over global consistency.

Do not "fix" unrelated inconsistencies while making a change.

---

## Core Principles

- Follow the existing architecture before introducing new abstractions.
- Prefer simple, explicit code over unnecessary indirection.
- Reuse existing utilities, infrastructure, and patterns.
- Keep responsibilities in the layer/module where they already belong.
- Minimize the scope of changes.
- Do not introduce dependencies unless the existing code cannot reasonably provide the functionality.
- Preserve existing public APIs unless the task explicitly requires changing them.
- Prefer immutable data when the codebase already follows that pattern.
- Validate data at clear boundaries.
- Keep side effects explicit.
- Avoid hidden global state.
- Prefer composition over inheritance unless inheritance is already part of the design.
- Do not introduce abstractions merely for theoretical extensibility.

---

## Naming

Follow the naming patterns already established by the project.

### Types

Use names that describe the role of the type rather than its implementation details.

Common roles include:

- `Service` — business/application logic
- `Repository` — persistent storage
- `Cache` — cached state
- `Controller` / `Handler` / `Route` — external request handling
- `Listener` — event/callback handling
- `Command` — command handling
- `Factory` — object creation
- `Builder` — incremental construction
- `Registry` — registration and lookup
- `Provider` — supplies or resolves a value
- `Resolver` — resolves an input into a value
- `Codec` / `Serializer` — encoding and decoding
- `Config` — configuration
- `Request` / `Response` — external API objects
- `Event` — local event
- `Message` / `Reply` — messaging objects

Do not add suffixes such as `Impl` unless the repository already uses them for that specific abstraction.

Avoid vague names such as:

- `Manager`
- `Helper`
- `Handler` when the type does not actually handle something
- `Utils`
- `Processor`

unless the surrounding code already establishes that convention.

### Methods

Method names should describe the operation being performed.

Prefer established verbs such as:

- `create`
- `build`
- `find`
- `get`
- `require`
- `resolve`
- `save`
- `update`
- `delete`
- `insert`
- `remove`
- `register`
- `unregister`
- `start`
- `stop`
- `close`
- `initialize`
- `publish`
- `send`
- `load`
- `parse`
- `encode`
- `decode`

Use the repository's existing naming distinction between operations that may return nothing, operations that may return `null`/absence, and operations that fail when a value is missing.

### Booleans

Use names that read naturally as predicates:

- `isX`
- `hasX`
- `canX`
- `supportsX`

Follow local conventions for boolean fields and properties.

---

## Formatting

Formatting consistency is a high-priority requirement. **Match the existing code before applying generic formatter defaults.**

### Indentation

- Use the indentation style already established by the repository.
- Default to **4 spaces** for indentation when no local convention exists.
- Never use tabs unless the repository consistently uses them.
- Continuation lines should use a consistent additional indentation level.
- Nested blocks must remain visually aligned with their parent construct.
- Keep chained calls aligned consistently.

Example:

```java
final var result = service.load(id)
        .map(this::transform)
        .orElseThrow();

```

### Braces

- Follow the repository's existing brace style.
- Use braces for multi-line control flow.
- Do not introduce a different brace style into an existing file.
- Keep opening and closing braces visually consistent.

```java
if (condition) {
    doSomething();
}

```

### Blank Lines

Blank lines are part of the code structure and should be used consistently.

Use blank lines to separate:

- fields from constructors;
- constructors from methods;
- logically distinct sections of a method;
- guard clauses from the main operation;
- setup from the main operation;
- related but independent blocks of code.

Avoid:

- multiple consecutive blank lines;
- removing intentional blank lines from existing code;
- adding blank lines between every statement;
- creating visually dense blocks with no separation.

Example:

```java
public void process(Request request) {
    validate(request);

    final var data = loadData(request);
    if (data.isEmpty()) {
        return;
    }

    save(data);
}

```

### Line Length

- Follow the repository's established line-length limit.
- When no limit is established, prefer approximately **120 columns**.
- Do not create extremely long lines merely to avoid wrapping.
- Do not wrap short expressions unnecessarily.
- Wrap long method calls, parameter lists, conditions, and expressions consistently with surrounding code.

### Method Calls and Chains

For short expressions, keep calls on one line.

```java
final var player = players.find(id);

```

For long chains, place each operation on its own line and align the chain consistently.

```java
final var result = repository.find(id)
        .map(this::transform)
        .orElseThrow();

```

Do not arbitrarily reformat existing chains.

### Parameters

Short parameter lists may remain on one line.

```java
public Result process(Request request, Context context) {
}

```

Long parameter lists should be wrapped consistently.

```java
public Result process(
        Request request,
        Context context,
        Configuration configuration
) {
}

```

Follow the surrounding file when deciding whether the closing delimiter belongs on its own line or with the final parameter.

### Conditions and Expressions

Wrap long conditions at logical boundaries.

```java
if (request.isValid()
        && request.hasPermission()
        && !request.isExpired()) {
    process(request);
}

```

Do not create arbitrary alignment that differs from nearby code.

### Collections

Short collections may remain inline.

```java
final var values = List.of("one", "two", "three");

```

Long collections should be formatted vertically.

```java
final var values = List.of(
        "one",
        "two",
        "three",
        "four");

```

Follow the repository's existing trailing-comma convention where the language supports it.

### Comments

Comments should follow the same indentation as the code they describe.

```java
if (cached != null) {
    // The cache may contain stale data during server startup.
    use(cached);
}

```

Do not place comments at arbitrary indentation levels or use spacing to visually separate comments from their associated code.

### Annotations and Modifiers

Follow the repository's existing placement and line-breaking conventions.

For example, if annotations are normally placed one per line:

```java
@Override
public @NotNull Result process(Request request) {
}

```

Do not collapse or rearrange annotations merely for stylistic preference.

### Imports

Keep imports formatted consistently with the surrounding file.

Do not reorder, group, collapse, or expand imports unrelated to the change.

### Type Layout

Keep members in the order established by the surrounding codebase.

A common structure is:

1. constants;
2. static state;
3. instance fields;
4. constructors;
5. factories;
6. public methods;
7. private methods;
8. nested types.

However, **the existing file takes precedence**.

### Formatting Rule

When modifying an existing file:

> **Do not reformat code you did not need to change.**

A small change should look like it was written by the same author who wrote the surrounding code.

Do not:

- run a formatter over the entire file unnecessarily;
- change indentation of unrelated code;
- normalize blank lines throughout the file;
- change brace style;
- reorder imports;
- change wrapping;
- convert between equivalent formatting styles.

Formatting consistency is more important than applying a theoretically preferred style.

---

## Classes and Types

Keep classes focused on one clear responsibility.

Prefer:

- small cohesive classes;
- explicit dependencies;
- immutable data structures where appropriate;
- constructors for required dependencies;
- factories for meaningful construction logic;
- records/data classes/value objects when supported and appropriate.

Avoid:

- unnecessary base classes;
- abstractions with only one implementation;
- interfaces created only because "interfaces are good practice";
- service locator patterns;
- unnecessary singleton patterns;
- large classes that combine unrelated responsibilities.

Use inheritance when the domain genuinely requires substitutability or when the framework requires it.

---

## Dependencies and Construction

Dependencies should be explicit.

Prefer constructor injection or the repository's established equivalent.

Avoid introducing:

- dependency injection frameworks;
- service locators;
- global mutable state;
- hidden dependency lookups;

when the project does not already use them.

Wire dependencies at the application's composition/root layer.

---

## Methods

Methods should have one clear purpose.

Prefer:

- guard clauses;
- shallow nesting;
- early returns for invalid or terminal conditions;
- small private methods for meaningful pieces of logic;
- method names that explain intent.

Avoid deeply nested control flow when guard clauses or extraction would make the code clearer.

Do not extract every few lines into a method merely to make a method shorter.

---

## Control Flow

Prefer the control-flow style already used by the project.

General principles:

- Use guard clauses for invalid or terminal conditions.
- Avoid unnecessary `else` blocks after a return.
- Avoid deeply nested conditions.
- Prefer pattern matching or modern language features when they are already used by the codebase.
- Use loops when they make side effects or early exits clearer.
- Use functional operations when they improve clarity rather than merely reducing line count.
- Do not use clever expressions when a straightforward branch is easier to understand.

---

## Collections and Data

Prefer immutable collections when the data should not change.

Use mutable collections only when mutation is actually required.

Do not expose internal mutable state directly.

When copying collections, use the repository's established defensive-copy pattern.

Choose collection types based on their required semantics:

- ordering;
- uniqueness;
- lookup;
- concurrency;
- mutability.

Do not choose a collection solely because it is familiar.

---

## Nullability and Optional Values

Follow the repository's established representation of absence.

Possible conventions include:

- nullable values;
- `Optional`/equivalent;
- result types;
- exceptions for required values.

Do not mix conventions arbitrarily.

If a value is required, fail clearly at the appropriate boundary.

If a value is legitimately optional, represent that explicitly according to the project's conventions.

---

## Error Handling

Errors should be handled at the layer that has enough context to make a meaningful decision.

Prefer domain-appropriate exceptions or result values already used by the project.

Do not:

- catch exceptions merely to rethrow them unchanged;
- swallow failures without an explicit reason;
- use exceptions for ordinary control flow when the project has an established result/outcome type;
- introduce checked exceptions into a codebase that does not use them.

When wrapping an exception:

- preserve the original cause;
- provide useful context;
- follow the repository's existing message format.

Error messages should explain what failed and, when useful, identify the relevant entity or operation.

---

## Logging

Follow the logging framework and conventions already used by the project.

General rules:

- `debug` for detailed diagnostic information;
- `info` for meaningful lifecycle or operational events;
- `warn` for recoverable or degraded situations;
- `error` for failures that require attention.

Include useful context such as:

- entity identifiers;
- operation names;
- relevant state;
- request/session identifiers when available.

Do not log:

- passwords;
- API keys;
- tokens;
- credentials;
- sensitive personal data;
- secrets contained in configuration.

Avoid logging the same failure multiple times at different layers unless each log adds meaningful context.

---

## Comments and Documentation

Comments should explain **why**, not restate **what** the code already says.

Good comments explain:

- non-obvious design decisions;
- workarounds;
- ordering requirements;
- framework behavior;
- performance constraints;
- concurrency assumptions;
- compatibility requirements;
- surprising implementation details.

Avoid comments such as:

```text
// Increment counter
counter++;

```

Do not add:

- commented-out code;
- unnecessary TODOs;
- comments that merely translate code into English;
- documentation that repeats the method/class name.

Public APIs should document behavior that cannot be understood from the signature alone, especially:

- threading requirements;
- lifecycle requirements;
- side effects;
- failure behavior;
- idempotency;
- important constraints.

---

## Concurrency and Async Code

Follow the repository's existing concurrency model.

Make thread boundaries explicit.

Do not perform blocking operations on event loops, UI threads, game threads, or other latency-sensitive threads.

Do not access thread-confined state from another thread.

Prefer the project's existing:

- executor;
- scheduler;
- async abstraction;
- synchronization primitives;
- cancellation model.

Avoid introducing a new concurrency abstraction when an existing one already solves the problem.

Document non-obvious thread-safety requirements.

---

## Architecture

Respect existing architectural boundaries.

Before adding code, determine:

1. Which module/package owns the responsibility?
2. Which layer should contain the behavior?
3. Which existing abstraction should be reused?
4. Where are dependencies normally wired?
5. Which thread/runtime owns the relevant state?

Keep:

- domain logic independent from infrastructure when the architecture allows it;
- external I/O at infrastructure boundaries;
- framework-specific wiring at framework boundaries;
- public API types separate from internal implementation types when the project does so.

Do not move code between layers unless the task requires it.

---

## APIs and Boundaries

External boundaries should use explicit request/response or input/output types when that is the repository convention.

Validate external input at the boundary.

Do not allow transport-specific concerns to leak unnecessarily into domain logic.

Keep serialization formats explicit and consistent.

Do not create a second serialization mechanism when the project already has one.

---

## Configuration

Use the project's existing configuration mechanism.

Do not introduce another configuration source without a concrete reason.

Configuration should:

- be validated at startup or at a clear boundary;
- have sensible defaults where appropriate;
- avoid exposing secrets in logs;
- use names consistent with the existing configuration scheme.

---

## Testing

Tests should verify behavior rather than implementation details.

Follow the repository's existing testing framework and style.

Prefer:

- deterministic tests;
- realistic test data;
- focused test cases;
- hand-written fakes when they make behavior clearer;
- mocks only where they are useful;
- isolated tests without external infrastructure unless integration testing is explicitly required.

Test names should describe behavior.

Keep tests structured clearly:

1. Arrange
2. Act
3. Assert

Do not introduce a new assertion, mocking, or test framework when the project already has an established one.

---

## External Infrastructure

Do not require external services in unit tests unless the test is explicitly an integration test.

For databases, caches, queues, APIs, and similar infrastructure:

- use fakes or mocks for unit tests;
- keep integration tests explicit;
- avoid depending on developer-local infrastructure;
- make external dependencies easy to replace in tests.

---

## Documentation and Generated Files

When changing behavior documented elsewhere, update the relevant documentation if the repository expects documentation to stay synchronized.

Do not update unrelated documentation.

Do not manually edit generated files unless the repository explicitly requires it.

---

## Changes

Make the smallest complete change that solves the task.

Before modifying code:

1. Read the target file.
2. Read nearby/sibling code.
3. Identify the local conventions.
4. Find existing utilities or abstractions that solve part of the problem.
5. Check whether tests exist for the affected area.

After modifying code:

- verify the changed behavior;
- add or update tests when appropriate;
- preserve unrelated code;
- avoid opportunistic refactoring;
- avoid formatting unrelated files;
- remove unused imports and variables introduced by the change.

---

## Anti-Patterns for AI Agents

Do not:

- invent architecture;
- introduce abstractions without a concrete need;
- create interfaces for single implementations without a real boundary;
- add `*Impl` classes without an established reason;
- add generic `Manager`, `Helper`, or `Utils` layers;
- introduce new dependencies for functionality already provided by the project;
- create duplicate infrastructure;
- introduce a second serialization/configuration/logging mechanism;
- rewrite existing code merely to match personal preferences;
- modernize unrelated code;
- rename unrelated symbols;
- reformat unrelated files;
- fix unrelated inconsistencies;
- add speculative features;
- add comments that merely describe the code;
- ignore existing tests or conventions.

---

## Rules for AI Agents

When working on this repository:

1. **Read before writing.**  
Inspect the target file and nearby code before making architectural or stylistic decisions.
2. **Copy local patterns.**  
The closest working example is usually more authoritative than a generic rule.
3. **Reuse before creating.**  
Search for existing utilities, services, abstractions, helpers, and infrastructure.
4. **Keep boundaries intact.**  
Put behavior in the layer responsible for it.
5. **Prefer explicit dependencies.**  
Make important dependencies visible rather than hiding them behind global state.
6. **Change only what is necessary.**  
Do not use the task as an excuse for unrelated refactoring.
7. **Preserve behavior.**  
A stylistic change must not accidentally alter runtime behavior.
8. **Test meaningful behavior.**  
Add or update tests when the affected area has tests and the change introduces new behavior.
9. **Follow repository conventions over personal preference.**  
Consistency is more important than theoretical "best practices."
10. **When uncertain, inspect more code before inventing a pattern.**  
Existing code is the primary source of truth.

