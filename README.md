# ![pym-json](image/pym-json.png "pym-json")

A lightweight, dependency-free JSON library for Java 21.

`pym-json` reads and writes JSON without any third-party dependencies. It maps
between JSON text and plain Java objects (POJOs, records, collections, maps,
arrays and scalars) using reflection, and also exposes a low-level
`JsonValue` tree for when you need full control.

|                      |                                                  |
|----------------------|--------------------------------------------------|
| **Group / Artifact** | `com.biroes.pym:pym-json`                        |
| **Homepage**         | https://www.biroes.com                           |
| **Issues**           | https://github.com/biroes/pym-json/issues        |
| **License**          | [MIT](https://opensource.org/licenses/MIT)       |
| **Maintainer**       | Andreas Schneider                                |
| **Contact**          | [andreas@biroes.com](mailto:andreas@biroes.com)  |
| **Company**          | biroes                                           |
| **Stack**            | Java 21+, Bash                                   |
| **Build**            | Maven 3.9+                                       |

## Contents

- [Features](#features)
- [Requirements](#requirements)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Annotations](#annotations)
- [Supported types](#supported-types)
- [Working with the raw tree](#working-with-the-raw-tree)
- [Behaviour notes](#behaviour-notes)
- [Building and testing](#building-and-testing)
- [`ppjson` — native JSON pretty-printer](#ppjson--native-json-pretty-printer)
- [License](#license)

## Features

- **Zero runtime dependencies** — just the JDK.
- **POJO and `record` binding** — serialize and deserialize plain classes and records.
- **Modern Java** — built on Java 21 features: sealed interfaces, records and pattern matching.
- **Streaming parser** — a recursive-descent parser that reads from a `Reader` without buffering the whole document as a string.
- **Configurable output** — pretty printing, custom indentation and optional null omission.
- **Annotations** — `@JsonProperty` to rename properties, `@JsonIgnore` to skip fields.
- **Raw tree access** — work directly with the `JsonValue` model when you need to.
- **Thread-safe** — a `Json` instance is immutable and safe to share; reflection metadata is cached per class.

## Requirements

- Java 21 or newer
- Maven (for building)

## Installation

The project is built with Maven:

```xml
<dependency>
    <groupId>com.biroes.pym</groupId>
    <artifactId>pym-json</artifactId>
    <version>1.0.0</version>
</dependency>
```

Build and test from source:

```bash
mvn clean install
```

## Quick start

Create a `Json` instance once and reuse it:

```java
import com.biroes.pym.json.Json;

Json json = Json.create();
```

### Serializing

```java
record Point(int x, int y, String label) {}

String text = json.write(new Point(1, 2, "origin"));
// {"x":1,"y":2,"label":"origin"}
```

### Deserializing

```java
// From a String
Point point = json.read("{\"x\":1,\"y\":2,\"label\":\"origin\"}", Point.class);

// From an InputStream (decoded as UTF-8; the stream is not closed)
try (InputStream in = ...) {
    Point fromStream = json.read(in, Point.class);
}
```

## Configuration

Customize output with a `JsonConfig`:

```java
import com.biroes.pym.json.JsonConfig;

Json pretty = Json.create(JsonConfig.builder()
        .prettyPrint(true)      // indented, multi-line output
        .indent("  ")           // indentation unit per nesting level
        .serializeNulls(false)  // omit null-valued properties
        .build());

System.out.println(pretty.write(new Point(1, 2, "origin")));
```

Output:

```json
{
  "x": 1,
  "y": 2,
  "label": "origin"
}
```

| Option           | Default | Description                                                    |
|------------------|---------|----------------------------------------------------------------|
| `prettyPrint`    | `false` | Indent and spread output over multiple lines.                  |
| `indent`         | `"  "`  | Indentation unit used per nesting level when pretty printing.  |
| `serializeNulls` | `true`  | Whether `null`-valued object properties are written or omitted.|

## Annotations

Rename a property with `@JsonProperty`, and skip a field with `@JsonIgnore`:

```java
import com.biroes.pym.json.annotation.JsonProperty;
import com.biroes.pym.json.annotation.JsonIgnore;

class User {
    @JsonProperty("first_name")
    private String firstName;

    private int age;

    @JsonIgnore
    private String passwordHash; // never serialized or populated
}
```

`@JsonProperty` works on both fields and record components.

## Supported types

| Category    | Types                                                                       |
|-------------|-----------------------------------------------------------------------------|
| Null        | `null`                                                                      |
| Scalars     | primitives and their wrappers, `String`, `CharSequence`, `char`/`Character` |
| Numbers     | `BigInteger`, `BigDecimal`, any `Number`                                    |
| Enums       | mapped to/from `Enum.name()`                                                |
| Collections | `Collection` (incl. `List`, `Set`) and Java arrays → JSON arrays            |
| Maps        | `Map` with string-convertible keys → JSON objects                           |
| Records     | deserialized via the canonical constructor                                  |
| POJOs       | plain classes with an accessible no-argument constructor                    |

### Collection and map target types

When deserializing into a concrete collection or map type, that type is honoured:

```java
TreeMap<String, Object> sorted = (TreeMap<String, Object>)
        json.read("{\"b\":2,\"a\":1}", TreeMap.class);

LinkedList<?> list = (LinkedList<?>) json.read("[1,2,3]", LinkedList.class);
```

When the requested type is an interface or abstract (e.g. `Map.class`,
`List.class`, `Set.class`), sensible defaults are used:
`LinkedHashMap`, `ArrayList` and `LinkedHashSet` respectively.

### Generic / untyped reading

Deserialize into `Object` to obtain a generic structure of `Map`, `List`,
`String`, `Boolean`, `BigDecimal` and `null`:

```java
Object value = json.read("{\"a\":[1,true,null,\"s\"]}", Object.class);
// Map<String, Object> { "a" -> List [ BigDecimal(1), true, null, "s" ] }
```

## Working with the raw tree

For full control, parse into the sealed `JsonValue` model instead of binding to
a Java type:

```java
import com.biroes.pym.json.model.JsonValue;

JsonValue tree = json.readTree("{\"a\":1,\"b\":[true,null]}");
// readTree(InputStream) is also available

if (tree instanceof JsonValue.JsonObject object) {
    JsonValue a = object.get("a");
}
```

`JsonValue` is a sealed interface with the following permitted subtypes, enabling
exhaustive `switch` pattern matching without a `default` branch:

- `JsonObject` — ordered name/value pairs
- `JsonArray` — ordered list of values
- `JsonString` — a string
- `JsonNumber` — a number (backed by `BigDecimal` to preserve precision)
- `JsonBoolean` — `true` or `false`
- `JsonNull` — the `null` literal

## Behaviour notes

- **UTF-8 by default.** `read(InputStream, ...)` decodes as UTF-8; an overload
  accepts an explicit `Charset`. A single leading UTF-8 byte-order mark (BOM) is
  skipped automatically.
- **Streams are not closed.** Callers retain ownership of any `InputStream` /
  `Writer` they pass in; writers are flushed but not closed.
- **Strict number handling.** JSON does not permit `NaN` or `Infinity`;
  attempting to serialize such a `float`/`double` throws a `JsonException`.
- **Errors.** All parsing, serialization and deserialization failures are
  reported as the unchecked `JsonException`.

## Building and testing

```bash
mvn compile      # compile the library
mvn test         # run the test suite
mvn clean install
```

## `ppjson` — native JSON pretty-printer

The repository ships a small command-line pretty-printer,
`com.biroes.pym.json.cli.PrettyPrintCli`, that reads JSON from a file argument
or standard input and writes it back formatted. It is built on top of
`pym-json` and is meant to be compiled to a standalone native binary with
[GraalVM](https://www.graalvm.org/) `native-image`.

### Usage

```
ppjson [options] [file]

  Reads JSON from <file> or, when omitted / '-', from standard
  input and writes it back pretty-printed to standard output.

Options:
  -i, --indent <N>   indent size in spaces (default 2)
  -t, --tab          use a tab per level (overrides --indent)
  -c, --compact      compact, single-line output
  -n, --no-nulls     omit object members whose value is null
  -h, --help         show this help and exit
```

Examples:

```bash
echo '{"a":1,"b":[2,3]}' | ppjson
ppjson --compact file.json
ppjson --indent 4 file.json
```

Exit codes: `0` success, `1` parse/I/O error, `2` usage error.

### Building the native binary

The `native` Maven profile wires up the
[`native-maven-plugin`](https://graalvm.github.io/native-build-tools/latest/):

```bash
mvn -Pnative -DskipTests package     # produces target/ppjson
```

When the `native-maven-plugin` cannot be resolved through the configured
Maven mirror (e.g. a corporate Artifactory proxy), use the helper script
which invokes `native-image` directly. It activates GraalVM via
[SDKMAN!](https://sdkman.io), builds the jar and compiles the binary:

```bash
./scripts/build-native.sh            # builds target/native/ppjson
./scripts/build-native.sh -v         # verbose native-image output
```

Requires a GraalVM JDK installed through SDKMAN! (default
`25.0.1-graal`; override with `GRAALVM_JAVA=<candidate>`). The resulting
binary is around 7 MB, starts in ~3 ms and has no JVM dependency.

Smoke-test the binary against `scripts/test.json`:

```bash
./scripts/test-native.sh
```

## License

pym-json is released under the [MIT License](https://opensource.org/licenses/MIT).

Copyright (c) 2026 biroes — Andreas Schneider &lt;andreas@biroes.com&gt;.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.


## Star History

<a href="https://www.star-history.com/?repos=biroes%2Fpym-json&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/chart?repos=biroes/pym-json&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/chart?repos=biroes/pym-json&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/chart?repos=biroes/pym-json&type=date&legend=top-left" />
 </picture>
</a>
