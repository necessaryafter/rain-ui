# Code Style

Operational guide for AI agents that write, change, or review code in this repository.

This document describes how code should be written **in this repository**. It is not a generic style guide.

## Rule Priority

The explicit rules in this document (early returns, blank lines, `final var`, import order, etc.) are mandatory. Existing
code that violates them is not a precedent: do not copy the violation into new code.

For everything this document does not decide explicitly, apply this order:

1. Code immediately around the change.
2. The current file and package/module conventions.
3. The dominant convention in the repository.
4. Generic language/framework conventions.

Do not "fix" unrelated inconsistencies while making a change. Code you write or rewrite must follow this document.

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

Use the repository's existing naming distinction between operations that may return nothing, operations that may return
`null`/absence, and operations that fail when a value is missing.

### Booleans

Use names that read naturally as predicates:

- `isX`
- `hasX`
- `canX`
- `supportsX`

Follow local conventions for boolean fields and properties.

---

## Formatting

Formatting consistency is a high-priority requirement. **Match the existing code before applying generic formatter
defaults.**

### Indentation

- Java: **4 spaces** per level, **8 spaces** for continuation lines (wrapped record components, `permits` lists, chained
  calls, wrapped arguments).
- TypeScript: **2 spaces** per level.
- Never use tabs. Never mix tabs and spaces in the same file.
- Nested blocks must remain visually aligned with their parent construct.

```java
public record Contract(
        int schemaVersion,
        String id,
        ComponentNode root) {
}

public sealed interface TypeSchema
        permits TypeSchema.StringType, TypeSchema.IntType, TypeSchema.LongType,
        TypeSchema.BoolType, TypeSchema.ListType, TypeSchema.ObjectType {
}

final var result = service.load(id)
        .map(this::transform)
        .orElseThrow();
```

### Braces

- Opening brace on the same line (K&R).
- Java: always use braces, even for single-statement `if`/`for`.
- TypeScript: a trivial guard may be a single line without braces (`if (typeof key !== "string") return undefined;`).
  Anything longer uses braces.
- A top-level type with an empty body closes on its own line. Nested one-line records may keep `{}`.

```java
public record ComponentNode(String type, Map<String, JsonNode> props, List<ComponentNode> children) {
}

public sealed interface TypeSchema permits ... {
    record StringType() implements TypeSchema {}
}
```

### Blank Lines

Blank lines are part of the code structure. A method body is a sequence of short paragraphs, not one dense block.

Rules:

- A variable and the guard that checks it form one paragraph: **no** blank line between them.
- **Always** a blank line after a guard block (`if` that returns, throws, `yield`s or `continue`s).
- **Always** a blank line before and after a loop, `try` block, or other multi-line block.
- Setup that only exists for a loop (a counter, the result collection) is its own paragraph, separated from the loop.
- A `return` that follows a block or a paragraph of statements gets a blank line before it.
- Several one-line guards that check the same thing may stay together as one paragraph.
- Separate fields from constructors, and each method from the next.
- Never more than one consecutive blank line. Never a blank line right after `{` or right before `}`.

```java
public Contract parse(String content) throws ParseException {
    final var contentBytes = content.getBytes(StandardCharsets.UTF_8);
    JsonNode root;

    try {
        root = mapper.readTree(content);
    } catch (Exception e) {
        throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "root");
    }

    if (!root.isObject()) {
        throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "root");
    }

    final var schemaVersionNode = root.get("schemaVersion");
    if (schemaVersionNode == null || !schemaVersionNode.isInt()) {
        throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, "schemaVersion");
    }

    final var schemaVersion = schemaVersionNode.asInt();
    ...
}

private List<ComponentNode> extractChildren(JsonNode node, String path, int depth) throws ParseException {
    final var childrenNode = node.get("children");
    if (childrenNode == null || !childrenNode.isArray()) {
        throw new ParseException(ValidationErrorCode.UNKNOWN_SCHEMA_VERSION, path);
    }

    final var children = new ArrayList<ComponentNode>();
    int index = 0;

    for (final var child : childrenNode) {
        children.add(parseComponentNode(child, path + ".children[" + index + "]", depth + 1));
        index++;
    }

    return children;
}
```

```ts
if (isBinding(propValue)) {
  const bindPath = propValue.$bind;
  const schema = scope[bindPath];

  if (!schema) return { ok: false, error: { code: "UNDECLARED_BINDING", path } };
  if (schema.kind !== "string") return { ok: false, error: { code: "BINDING_TYPE_MISMATCH", path } };

  return { ok: true };
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

Follow the surrounding file when deciding whether the closing delimiter belongs on its own line or with the final
parameter.

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

TypeScript: multi-line object literals, parameter lists and argument lists end with a trailing comma, one entry per line.

```ts
return {
  $bind: path.join("."),
};
```

### Comments

Comments should follow the same indentation as the code they describe.

```java
if (cached != null) {
    // The cache may contain stale data during server startup.
    use(cached);
}

```

Do not place comments at arbitrary indentation levels or use spacing to visually separate comments from their associated
code.

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

Java imports are grouped in this order, each group alphabetical and separated by one blank line:

1. everything that is not `java.*`/`javax.*` (third-party and project packages together);
2. `java.*` and `javax.*`;
3. `import static`.

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.rainframework.ui.protocol.validation.ValidationErrorCode;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
```

Remove unused imports. Use fully qualified names inline only to resolve a real clash.

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

A small change should look like it was written by the same author who wrote the surrounding code — as long as that code
follows this document.

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

Do not extract every few lines into a method merely to make a method shorter.

---

## Local Variables (Java)

Declare locals with `final var`, including enhanced `for` variables, iterator variables and try-with-resources.

```java
for (final var it = propsNode.fields(); it.hasNext(); ) {
    final var entry = it.next();

    props.put(entry.getKey(), entry.getValue());
}

try (final var stream = Files.list(validDir)) {
    ...
}
```

Use an explicit type without `final` only when the variable is reassigned (counters, a variable assigned inside `try`):

```java
int index = 0;
JsonNode root;
```

Build collections with the type on the constructor: `final var props = new HashMap<String, JsonNode>();`.

---

## Control Flow

These are mandatory, not preferences:

- **Early return.** Handle invalid and terminal cases first with a guard (`return`, `throw`, `continue`, `yield`), then
  write the main path unindented.
- **No `else` after a branch that exits.** If the `if` returns/throws/yields, the rest is simply the next statement.
- **No `else if` chains.** Two branches is the maximum. With more, use a `switch` on the discriminant, independent guard
  `if`s that each exit, or a lookup table.
- **Maximum two levels of nesting** inside a method body. Deeper than that, invert the condition into a guard or extract
  a method.
- Use `switch` expressions and pattern matching (`instanceof TypeSchema.ListType listType`) instead of casts and chains.
- Use loops when they make side effects or early exits clearer; use streams only when they are clearer, not shorter.
- Do not use clever expressions when a straightforward branch is easier to understand.

Bad:

```ts
if (propName === "action") {
  if (typeof propValue === "string") {
    ...
  } else {
    return { ok: false, ... };
  }
} else if (propName === "disabled") {
  return this.validateBoolOrBinding(propValue, path, scope);
} else if (propName === "payload") {
  return { ok: true };
}
```

Good:

```ts
switch (propName) {
  case "action":
    return this.validateActionProp(propValue, path);
  case "disabled":
    return this.validateBoolOrBinding(propValue, path, scope);
  case "payload":
    // Paired validation happens in validateProps after all per-key checks.
    return { ok: true };
}
```

```java
if (!isBinding(sourceBinding)) {
    return ValidationResult.fail(ValidationErrorCode.INVALID_PROP_TYPE, path);
}

final var bindPath = sourceBinding.get("$bind").asText();
...
```

---

## TypeScript

- No `as any`. Narrow through the discriminant (`entry.kind === "object"`) so the compiler knows the type; if a cast is
  truly needed, cast to the precise type (`{} as { [K in keyof Schema]: Binding<unknown> }`).
- Name unused callback parameters `_`.
- Prefer short names when the scope is small and the meaning is obvious: `entry`, `path`, `prefix`, not `schemaEntry`,
  `fullPath`, `scopePrefix`.
- Build paths as arrays and join once (`[...prefix, key]` then `path.join(".")`) instead of branching on emptiness.
- A long generic parameter list is wrapped one parameter per line, like any other parameter list:

```ts
export function createPropertyProxy<
  Schema extends Record<string, TypeSchema>
>(
  schema: Schema,
  prefix: string[] = [],
): { [K in keyof Schema]: Binding<unknown> } {
```

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

Group Java types by feature into subpackages once a feature has more than one type. Example: the validator, its result,
error and error code live in `com.rainframework.ui.protocol.validation`, not flat in `protocol`.

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

When changing behavior documented elsewhere, update the relevant documentation if the repository expects documentation
to stay synchronized.

Do not update unrelated documentation.

Markdown files wrap prose at **120 columns**. Continuation lines of a list item are indented to align with the item's
text. A bold label followed by a list (`**Why:**`) gets a blank line before the list, and never has trailing whitespace.

```markdown
**Why:**

- Mojang released official mappings, and Fabric now recommends them over Yarn as the primary mappings source, even for
  versions like 1.21.1 that predate the unobfuscated release.
```

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

