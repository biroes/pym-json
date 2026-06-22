# AGENTS.md

Lightweight, zero-dependency JSON library for Java 21. Single Maven module (`com.biroes.pym:pym-json`).

## Build & test

- Build: `mvn compile` — Test: `mvn test` — Full: `mvn clean install`
- No Maven wrapper (`mvnw`); use a system `mvn`.
- Requires **JDK 21** (`maven.compiler.release=21`). Code uses sealed interfaces, records, and pattern-matching `switch` (the last of these is final only in 21); older JDKs will not compile.
- Run a single test: `mvn test -Dtest=JsonTest#methodName`
- All tests live in one class: `src/test/java/com/biroes/pym/json/JsonTest.java` (JUnit 5 / Jupiter).
- Current version is `1.0.0` (release). Keep `pom.xml` `version` in sync with the version shown in `README.md`.

## `ppjson` CLI & native image

- `src/main/java/com/biroes/pym/json/cli/PrettyPrintCli.java` is a small CLI that reads JSON from a file or stdin and writes it pretty-printed using `Json.readTree` + `Json.write(tree, out)`. Main class is `com.biroes.pym.json.cli.PrettyPrintCli`; image name is `ppjson`.
- Options: `--indent N`, `--tab`, `--compact`, `--no-nulls`, `--help`. Exit codes: 0 ok, 1 parse/I/O error, 2 usage error. `-` as the file argument means stdin.
- The `native` Maven profile (`native-maven-plugin` `1.1.2`) builds a standalone binary. Run with `mvn -Pnative -DskipTests package`.
- The configured Maven mirror (`mirrorOf=*` in `~/.m2/settings.xml`) does **not** proxy the GraalVM plugin, so `mvn -Pnative` typically fails with "Unresolveable build extension". In that case use `scripts/build-native.sh`, which activates GraalVM via SDKMAN! and invokes `native-image` directly on the built jar. Default GraalVM candidate: `25.0.1-graal` (override via `GRAALVM_JAVA`).
- `scripts/test-native.sh` smoke-tests `target/native/ppjson` against `scripts/test.json` in all modes (pretty, compact, indent=4, tab, no-nulls, stdin, stdin via `-`).
- GraalVM is activated via SDKMAN! in the scripts; `sdkman-init.sh` references variables that are unset under `set -u`, so the scripts temporarily disable `set -u` around `source` + `sdk use`.
- Native build flags: `--no-fallback`, `-H:+ReportExceptionStackTraces`, `--install-exit-handlers`. Do not introduce a fallback image; the CLI must be fully ahead-of-time compiled.

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

## Releasing

- Artifacts (jar, sources, javadoc) are published to Maven Central via the `central-publishing-maven-plugin` (`publishingServerId=central`).
- The `release` profile activates GPG signing of artifacts (`maven-gpg-plugin`) — required for Central. Run with `mvn clean install -Prelease`.
- `maven-enforcer-plugin` enforces Maven ≥ 3.9 and JDK ≥ 21 at build time.
- When releasing, drop the `-SNAPSHOT` suffix from `pom.xml` **and** the README dependency snippet; tag the release.
