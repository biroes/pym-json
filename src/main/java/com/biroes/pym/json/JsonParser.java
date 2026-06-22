package com.biroes.pym.json;

import com.biroes.pym.json.model.JsonValue;
import com.biroes.pym.json.model.JsonValue.JsonArray;
import com.biroes.pym.json.model.JsonValue.JsonBoolean;
import com.biroes.pym.json.model.JsonValue.JsonNull;
import com.biroes.pym.json.model.JsonValue.JsonNumber;
import com.biroes.pym.json.model.JsonValue.JsonObject;
import com.biroes.pym.json.model.JsonValue.JsonString;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;

/**
 * A streaming, recursive-descent JSON parser that reads characters from a
 * {@link Reader} and builds a {@link JsonValue} tree.
 * <p>
 * The parser is single-use and not thread safe. It reads one character of
 * look-ahead at a time and therefore never buffers the whole document in memory
 * as a string.
 */
final class JsonParser {

    private static final int EOF = -1;
    private static final int BOM = '\uFEFF';

    private final Reader reader;
    private int current;     // current look-ahead character, or EOF
    private long position;   // number of characters consumed (for error messages)

    JsonParser(Reader reader) {
        this.reader = reader;
    }

    /**
     * Parses a single JSON value from the underlying reader and verifies that
     * only whitespace follows it.
     *
     * @return the parsed {@link JsonValue}
     */
    JsonValue parse() {
        advance(); // prime the first character
        if (current == BOM) {
            advance(); // skip a single leading UTF-8 byte-order mark
        }
        skipWhitespace();
        JsonValue value = parseValue();
        skipWhitespace();
        if (current != EOF) {
            throw error("Unexpected trailing character '" + (char) current + "'");
        }
        return value;
    }

    private JsonValue parseValue() {
        return switch (current) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> new JsonString(parseString());
            case 't', 'f' -> parseBoolean();
            case 'n' -> parseNull();
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> parseNumber();
            case EOF -> throw error("Unexpected end of input, expected a value");
            default -> throw error("Unexpected character '" + (char) current + "', expected a value");
        };
    }

    private JsonObject parseObject() {
        JsonObject object = new JsonObject();
        advance(); // consume '{'
        skipWhitespace();
        if (current == '}') {
            advance();
            return object;
        }
        while (true) {
            skipWhitespace();
            if (current != '"') {
                throw error("Expected a string key in object");
            }
            String key = parseString();
            skipWhitespace();
            if (current != ':') {
                throw error("Expected ':' after object key");
            }
            advance(); // consume ':'
            skipWhitespace();
            object.put(key, parseValue());
            skipWhitespace();
            switch (current) {
                case ',' -> advance();
                case '}' -> {
                    advance();
                    return object;
                }
                default -> throw error("Expected ',' or '}' in object");
            }
        }
    }

    private JsonArray parseArray() {
        JsonArray array = new JsonArray();
        advance(); // consume '['
        skipWhitespace();
        if (current == ']') {
            advance();
            return array;
        }
        while (true) {
            skipWhitespace();
            array.add(parseValue());
            skipWhitespace();
            switch (current) {
                case ',' -> advance();
                case ']' -> {
                    advance();
                    return array;
                }
                default -> throw error("Expected ',' or ']' in array");
            }
        }
    }

    private String parseString() {
        // current == '"'
        advance(); // consume opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (current == EOF) {
                throw error("Unterminated string");
            }
            char c = (char) current;
            if (c == '"') {
                advance(); // consume closing quote
                return sb.toString();
            }
            if (c == '\\') {
                advance();
                sb.append(parseEscape());
            } else if (c < 0x20) {
                throw error("Unescaped control character in string (0x"
                        + Integer.toHexString(c) + ")");
            } else {
                sb.append(c);
                advance();
            }
        }
    }

    private char parseEscape() {
        int c = current;
        return switch (c) {
            case '"' -> consumeAnd('"');
            case '\\' -> consumeAnd('\\');
            case '/' -> consumeAnd('/');
            case 'b' -> consumeAnd('\b');
            case 'f' -> consumeAnd('\f');
            case 'n' -> consumeAnd('\n');
            case 'r' -> consumeAnd('\r');
            case 't' -> consumeAnd('\t');
            case 'u' -> parseUnicodeEscape();
            case EOF -> throw error("Unterminated escape sequence");
            default -> throw error("Invalid escape sequence '\\" + (char) c + "'");
        };
    }

    private char consumeAnd(char replacement) {
        advance();
        return replacement;
    }

    private char parseUnicodeEscape() {
        advance(); // consume 'u'
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int digit = Character.digit(current, 16);
            if (digit < 0) {
                throw error("Invalid \\u escape: expected hex digit");
            }
            value = (value << 4) | digit;
            advance();
        }
        return (char) value;
    }

    private JsonBoolean parseBoolean() {
        if (current == 't') {
            expectLiteral("true");
            return JsonBoolean.TRUE;
        }
        expectLiteral("false");
        return JsonBoolean.FALSE;
    }

    private JsonNull parseNull() {
        expectLiteral("null");
        return JsonNull.INSTANCE;
    }

    private void expectLiteral(String literal) {
        for (int i = 0; i < literal.length(); i++) {
            if (current != literal.charAt(i)) {
                throw error("Invalid literal, expected '" + literal + "'");
            }
            advance();
        }
    }

    private JsonNumber parseNumber() {
        StringBuilder sb = new StringBuilder();
        if (current == '-') {
            sb.append('-');
            advance();
        }
        readDigits(sb, true);
        if (current == '.') {
            sb.append('.');
            advance();
            readDigits(sb, false);
        }
        if (current == 'e' || current == 'E') {
            sb.append((char) current);
            advance();
            if (current == '+' || current == '-') {
                sb.append((char) current);
                advance();
            }
            readDigits(sb, false);
        }
        try {
            return new JsonNumber(new BigDecimal(sb.toString()));
        } catch (NumberFormatException e) {
            throw error("Invalid number '" + sb + "'");
        }
    }

    private void readDigits(StringBuilder sb, boolean integerPart) {
        if (!isDigit(current)) {
            throw error("Invalid number: expected a digit");
        }
        // A leading zero in the integer part must not be followed by more digits.
        boolean leadingZero = integerPart && current == '0';
        sb.append((char) current);
        advance();
        if (leadingZero && isDigit(current)) {
            throw error("Invalid number: leading zeros are not allowed");
        }
        while (isDigit(current)) {
            sb.append((char) current);
            advance();
        }
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    private void skipWhitespace() {
        while (current == ' ' || current == '\t' || current == '\n' || current == '\r') {
            advance();
        }
    }

    private void advance() {
        try {
            current = reader.read();
            if (current != EOF) {
                position++;
            }
        } catch (IOException e) {
            throw new JsonException("I/O error while reading JSON input", e);
        }
    }

    private JsonException error(String message) {
        return new JsonException(message + " (at character " + position + ")");
    }
}
