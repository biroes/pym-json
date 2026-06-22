# AGENTS.md

Lightweight, zero-dependency JSON library for Java 21. Single Maven module (`com.biroes.pym:pym-json`).

## Build & test

- Build: `mvn compile` — Test: `mvn test` — Full: `mvn clean install`
- No Maven wrapper (`mvnw`); use a system `mvn`.
- Requires **JDK 21** (`maven.compiler.release=21`). Code uses sealed interfaces, records, and pattern-matching `switch` (the last of these is final only in 21); older JDKs will not compile.
- Run a single test: `mvn test -Dtest=JsonTest#methodName`
- All tests live in one class: `src/test/java/com/biroes/pym/json/JsonTest.java` (JUnit 5 / Jupiter).

## Architecture (not obvious from filenames)

- Public API surface is intentionally tiny: only `Json`, `JsonConfig`, `JsonException`, the `model.JsonValue` tree, and the two annotations are `public`. `ObjectMapper`, `JsonParser`, `JsonWriter` are **package-private** — keep them that way; expose features through `Json`.
- `Json` is the entry point and is immutable/thread-safe. Reuse one instance.
- Flow: `Json` -> `JsonParser` (Reader -> `JsonValue` tree) -> `ObjectMapper` (tree <-> Java objects); `JsonWriter` does tree -> text.

## Conventions & invariants

- All numbers are backed by `BigDecimal` (`JsonNumber`) to preserve precision — do not introduce `double`-based number paths.
- `ObjectMapper` rejects `NaN`/`Infinity` with `JsonException` (JSON has no such literals). Preserve this.
- Reflection metadata is cached per class in `ObjectMapper.FIELD_CACHE` (`ConcurrentHashMap`); reuse it rather than re-scanning fields.
- All failures surface as the unchecked `JsonException`.
- Streams/Writers passed to `Json` are **not closed** (writers are flushed); caller owns them.
- `@JsonProperty` renames, `@JsonIgnore` skips; both honored for POJO fields and record components.
