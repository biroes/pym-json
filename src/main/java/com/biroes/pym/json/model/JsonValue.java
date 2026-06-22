package com.biroes.pym.json.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The sealed type hierarchy representing an in-memory JSON document tree.
 * <p>
 * A {@code JsonValue} is exactly one of:
 * <ul>
 *     <li>{@link JsonObject} - an ordered collection of name/value pairs</li>
 *     <li>{@link JsonArray} - an ordered list of values</li>
 *     <li>{@link JsonString} - a string</li>
 *     <li>{@link JsonNumber} - a number (backed by {@link java.math.BigDecimal})</li>
 *     <li>{@link JsonBoolean} - {@code true} or {@code false}</li>
 *     <li>{@link JsonNull} - the {@code null} literal</li>
 * </ul>
 * Being a sealed hierarchy, exhaustive {@code switch} pattern matching can be used
 * over all permitted subtypes without a {@code default} branch.
 */
public sealed interface JsonValue {

    /** A JSON object: an ordered map of string keys to {@link JsonValue}s. */
    final class JsonObject implements JsonValue {
        private final Map<String, JsonValue> members;

        public JsonObject() {
            this.members = new LinkedHashMap<>();
        }

        public JsonObject(Map<String, JsonValue> members) {
            this.members = new LinkedHashMap<>(Objects.requireNonNull(members, "members"));
        }

        public JsonObject put(String key, JsonValue value) {
            members.put(Objects.requireNonNull(key, "key"),
                    value == null ? JsonNull.INSTANCE : value);
            return this;
        }

        public JsonValue get(String key) {
            return members.get(key);
        }

        public boolean has(String key) {
            return members.containsKey(key);
        }

        public Map<String, JsonValue> members() {
            return Collections.unmodifiableMap(members);
        }

        public int size() {
            return members.size();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof JsonObject other && members.equals(other.members);
        }

        @Override
        public int hashCode() {
            return members.hashCode();
        }

        @Override
        public String toString() {
            return "JsonObject" + members;
        }
    }

    /** A JSON array: an ordered list of {@link JsonValue}s. */
    final class JsonArray implements JsonValue {
        private final List<JsonValue> elements;

        public JsonArray() {
            this.elements = new ArrayList<>();
        }

        public JsonArray(List<JsonValue> elements) {
            this.elements = new ArrayList<>(Objects.requireNonNull(elements, "elements"));
        }

        public JsonArray add(JsonValue value) {
            elements.add(value == null ? JsonNull.INSTANCE : value);
            return this;
        }

        public JsonValue get(int index) {
            return elements.get(index);
        }

        public List<JsonValue> elements() {
            return Collections.unmodifiableList(elements);
        }

        public int size() {
            return elements.size();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof JsonArray other && elements.equals(other.elements);
        }

        @Override
        public int hashCode() {
            return elements.hashCode();
        }

        @Override
        public String toString() {
            return "JsonArray" + elements;
        }
    }

    /** A JSON string literal. */
    record JsonString(String value) implements JsonValue {
        public JsonString {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A JSON number, backed by {@link java.math.BigDecimal} to preserve precision
     * regardless of whether the source was an integer or a floating point value.
     */
    record JsonNumber(java.math.BigDecimal value) implements JsonValue {
        public JsonNumber {
            Objects.requireNonNull(value, "value");
        }

        public static JsonNumber of(long v) {
            return new JsonNumber(java.math.BigDecimal.valueOf(v));
        }

        public static JsonNumber of(double v) {
            return new JsonNumber(java.math.BigDecimal.valueOf(v));
        }
    }

    /** A JSON boolean literal ({@code true} or {@code false}). */
    record JsonBoolean(boolean value) implements JsonValue {
        public static final JsonBoolean TRUE = new JsonBoolean(true);
        public static final JsonBoolean FALSE = new JsonBoolean(false);

        public static JsonBoolean of(boolean v) {
            return v ? TRUE : FALSE;
        }
    }

    /** The JSON {@code null} literal. Singleton. */
    final class JsonNull implements JsonValue {
        public static final JsonNull INSTANCE = new JsonNull();

        private JsonNull() {
        }

        @Override
        public String toString() {
            return "null";
        }
    }
}
