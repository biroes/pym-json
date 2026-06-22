package com.biroes.pym.json;

import com.biroes.pym.json.model.JsonValue;
import com.biroes.pym.json.model.JsonValue.JsonArray;
import com.biroes.pym.json.model.JsonValue.JsonBoolean;
import com.biroes.pym.json.model.JsonValue.JsonNull;
import com.biroes.pym.json.model.JsonValue.JsonNumber;
import com.biroes.pym.json.model.JsonValue.JsonObject;
import com.biroes.pym.json.model.JsonValue.JsonString;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Map;

/**
 * Serializes a {@link JsonValue} tree to a character {@link Writer}, honouring the
 * supplied {@link JsonConfig} (pretty printing, indentation and null handling).
 */
final class JsonWriter {

    private final Writer out;
    private final JsonConfig config;

    JsonWriter(Writer out, JsonConfig config) {
        this.out = out;
        this.config = config;
    }

    void write(JsonValue value) {
        try {
            writeValue(value, 0);
        } catch (IOException e) {
            throw new UncheckedIOException("I/O error while writing JSON output", e);
        }
    }

    private void writeValue(JsonValue value, int depth) throws IOException {
        switch (value) {
            case JsonObject object -> writeObject(object, depth);
            case JsonArray array -> writeArray(array, depth);
            case JsonString s -> writeString(s.value());
            case JsonNumber n -> out.write(n.value().toString());
            case JsonBoolean b -> out.write(b.value() ? "true" : "false");
            case JsonNull ignored -> out.write("null");
        }
    }

    private void writeObject(JsonObject object, int depth) throws IOException {
        Map<String, JsonValue> members = object.members();
        if (members.isEmpty()) {
            out.write("{}");
            return;
        }
        out.write('{');
        int childDepth = depth + 1;
        boolean first = true;
        for (Map.Entry<String, JsonValue> entry : members.entrySet()) {
            JsonValue v = entry.getValue();
            if (!config.serializeNulls() && v instanceof JsonNull) {
                continue;
            }
            if (!first) {
                out.write(',');
            }
            first = false;
            newlineAndIndent(childDepth);
            writeString(entry.getKey());
            out.write(':');
            if (config.prettyPrint()) {
                out.write(' ');
            }
            writeValue(v, childDepth);
        }
        // If serializeNulls omitted everything, emit an empty object cleanly.
        if (first) {
            out.write('}');
            return;
        }
        newlineAndIndent(depth);
        out.write('}');
    }

    private void writeArray(JsonArray array, int depth) throws IOException {
        if (array.size() == 0) {
            out.write("[]");
            return;
        }
        out.write('[');
        int childDepth = depth + 1;
        boolean first = true;
        for (JsonValue element : array.elements()) {
            if (!first) {
                out.write(',');
            }
            first = false;
            newlineAndIndent(childDepth);
            writeValue(element, childDepth);
        }
        newlineAndIndent(depth);
        out.write(']');
    }

    private void newlineAndIndent(int depth) throws IOException {
        if (!config.prettyPrint()) {
            return;
        }
        out.write('\n');
        String indent = config.indent();
        for (int i = 0; i < depth; i++) {
            out.write(indent);
        }
    }

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private void writeString(String value) throws IOException {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append("\\u")
                                .append(HEX[(c >> 12) & 0xF])
                                .append(HEX[(c >> 8) & 0xF])
                                .append(HEX[(c >> 4) & 0xF])
                                .append(HEX[c & 0xF]);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        out.write(sb.toString());
    }
}
