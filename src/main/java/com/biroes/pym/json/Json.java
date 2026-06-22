package com.biroes.pym.json;

import com.biroes.pym.json.model.JsonValue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Entry point of the pym-json library: a lightweight, dependency-free JSON
 * processor for Java 21.
 * <p>
 * Instances are immutable and thread safe; create one (optionally with a custom
 * {@link JsonConfig}) and reuse it:
 * <pre>{@code
 * Json json = Json.create(JsonConfig.builder().prettyPrint(true).build());
 *
 * // Deserialize from an InputStream into a POJO or record
 * try (InputStream in = ...) {
 *     User user = json.read(in, User.class);
 * }
 *
 * // Serialize a POJO back to a JSON string
 * String text = json.write(user);
 * }</pre>
 *
 * @see JsonConfig
 * @see com.biroes.pym.json.annotation.JsonProperty
 * @see com.biroes.pym.json.annotation.JsonIgnore
 */
public final class Json {

    private final JsonConfig config;
    private final ObjectMapper mapper;

    private Json(JsonConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        this.mapper = new ObjectMapper();
    }

    /**
     * Creates a {@code Json} instance using the {@linkplain JsonConfig#defaults() default configuration}.
     *
     * @return a new {@code Json} instance
     */
    public static Json create() {
        return new Json(JsonConfig.defaults());
    }

    /**
     * Creates a {@code Json} instance using the supplied configuration.
     *
     * @param config the configuration to use
     * @return a new {@code Json} instance
     */
    public static Json create(JsonConfig config) {
        return new Json(config);
    }

    /**
     * @return the configuration this instance was created with
     */
    public JsonConfig config() {
        return config;
    }

    // ------------------------------------------------------------------
    // Reading / deserialization
    // ------------------------------------------------------------------

    /**
     * Parses JSON read from the given {@link InputStream} (decoded as UTF-8) and
     * deserializes it into an instance of {@code type}.
     * <p>
     * The stream is fully consumed but <strong>not</strong> closed; the caller
     * remains responsible for closing it.
     *
     * @param input the input stream to read from
     * @param type  the target type (POJO, record, collection, map, array or scalar)
     * @param <T>   the target type
     * @return the deserialized object
     * @throws JsonException if the input is not valid JSON or cannot be mapped to {@code type}
     */
    public <T> T read(InputStream input, Class<T> type) {
        return read(input, StandardCharsets.UTF_8, type);
    }

    /**
     * Parses JSON read from the given {@link InputStream} using the specified
     * {@link Charset} and deserializes it into an instance of {@code type}.
     *
     * @param input   the input stream to read from
     * @param charset the charset used to decode the stream
     * @param type    the target type
     * @param <T>     the target type
     * @return the deserialized object
     * @throws JsonException if the input is not valid JSON or cannot be mapped to {@code type}
     */
    public <T> T read(InputStream input, Charset charset, Class<T> type) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(charset, "charset");
        Objects.requireNonNull(type, "type");
        Reader reader = new BufferedReader(new InputStreamReader(input, charset));
        return mapper.fromJson(parseTree(reader), type);
    }

    /**
     * Parses JSON from a {@link String} and deserializes it into {@code type}.
     *
     * @param json the JSON text
     * @param type the target type
     * @param <T>  the target type
     * @return the deserialized object
     * @throws JsonException if the input is not valid JSON or cannot be mapped to {@code type}
     */
    public <T> T read(String json, Class<T> type) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(type, "type");
        return mapper.fromJson(parseTree(new StringReader(json)), type);
    }

    /**
     * Parses JSON from the given {@link InputStream} (UTF-8) into a raw
     * {@link JsonValue} tree without binding it to a Java type.
     *
     * @param input the input stream to read from
     * @return the parsed {@link JsonValue} tree
     * @throws JsonException if the input is not valid JSON
     */
    public JsonValue readTree(InputStream input) {
        Objects.requireNonNull(input, "input");
        Reader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));
        return parseTree(reader);
    }

    /**
     * Parses JSON from a {@link String} into a raw {@link JsonValue} tree without
     * binding it to a Java type.
     *
     * @param json the JSON text
     * @return the parsed {@link JsonValue} tree
     * @throws JsonException if the input is not valid JSON
     */
    public JsonValue readTree(String json) {
        Objects.requireNonNull(json, "json");
        return parseTree(new StringReader(json));
    }

    private JsonValue parseTree(Reader reader) {
        return new JsonParser(reader).parse();
    }

    // ------------------------------------------------------------------
    // Writing / serialization
    // ------------------------------------------------------------------

    /**
     * Serializes the given object to a JSON {@link String}, formatted according to
     * this instance's {@link JsonConfig}.
     *
     * @param value the object to serialize (may be {@code null})
     * @return the JSON representation
     * @throws JsonException if the object cannot be serialized
     */
    public String write(Object value) {
        StringWriter writer = new StringWriter();
        write(value, writer);
        return writer.toString();
    }

    /**
     * Serializes the given object as JSON to the supplied {@link Writer}, formatted
     * according to this instance's {@link JsonConfig}. The writer is flushed but not
     * closed.
     *
     * @param value the object to serialize (may be {@code null})
     * @param out   the writer to write to
     * @throws JsonException if the object cannot be serialized
     */
    public void write(Object value, Writer out) {
        Objects.requireNonNull(out, "out");
        JsonValue tree = mapper.toJson(value);
        new JsonWriter(out, config).write(tree);
        try {
            out.flush();
        } catch (java.io.IOException e) {
            throw new JsonException("I/O error while flushing JSON output", e);
        }
    }
}
