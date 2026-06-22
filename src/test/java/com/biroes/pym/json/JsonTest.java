package com.biroes.pym.json;

import com.biroes.pym.json.annotation.JsonIgnore;
import com.biroes.pym.json.annotation.JsonProperty;
import com.biroes.pym.json.model.JsonValue;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    private final Json json = Json.create();

    // ---- Test fixtures -------------------------------------------------

    enum Role { ADMIN, USER }

    static class User {
        @JsonProperty("first_name")
        private String firstName;
        private int age;
        private Role role;
        private List<String> tags;
        @JsonIgnore
        private String passwordHash;

        User() {
        }

        User(String firstName, int age, Role role, List<String> tags, String passwordHash) {
            this.firstName = firstName;
            this.age = age;
            this.role = role;
            this.tags = tags;
            this.passwordHash = passwordHash;
        }
    }

    record Point(int x, int y, @JsonProperty("label") String name) {
    }

    record Wrapper(Point point, List<Point> points, Map<String, Integer> scores) {
    }

    static class Numbers {
        BigDecimal decimal;
        BigInteger integer;
        char grade;

        Numbers() {
        }

        Numbers(BigDecimal decimal, BigInteger integer, char grade) {
            this.decimal = decimal;
            this.integer = integer;
            this.grade = grade;
        }
    }

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    // ---- Deserialization ----------------------------------------------

    @Test
    void readPojoWithRenamedAndIgnoredFields() {
        String input = """
                {
                  "first_name": "Ada",
                  "age": 36,
                  "role": "ADMIN",
                  "tags": ["math", "cs"],
                  "passwordHash": "should-be-ignored"
                }""";

        User user = json.read(stream(input), User.class);

        assertEquals("Ada", user.firstName);
        assertEquals(36, user.age);
        assertEquals(Role.ADMIN, user.role);
        assertEquals(List.of("math", "cs"), user.tags);
        assertNull(user.passwordHash, "@JsonIgnore field must not be populated");
    }

    @Test
    void readRecordWithRenamedComponent() {
        Point point = json.read(stream("{\"x\":1,\"y\":2,\"label\":\"origin\"}"), Point.class);
        assertEquals(new Point(1, 2, "origin"), point);
    }

    @Test
    void readNestedRecordsCollectionsAndMaps() {
        String input = """
                {
                  "point": {"x": 1, "y": 2, "label": "a"},
                  "points": [{"x": 3, "y": 4, "label": "b"}],
                  "scores": {"alice": 10, "bob": 20}
                }""";

        Wrapper wrapper = json.read(stream(input), Wrapper.class);

        assertEquals(new Point(1, 2, "a"), wrapper.point());
        assertEquals(List.of(new Point(3, 4, "b")), wrapper.points());
        assertEquals(Map.of("alice", 10, "bob", 20), wrapper.scores());
    }

    @Test
    void readFromString() {
        Point point = json.read("{\"x\":5,\"y\":6,\"label\":\"p\"}", Point.class);
        assertEquals(new Point(5, 6, "p"), point);
    }

    @Test
    @SuppressWarnings("unchecked")
    void readTreeAsGenericObject() {
        Object result = json.read("{\"a\":[1,true,null,\"s\"]}", Object.class);
        assertInstanceOf(Map.class, result);
        Map<String, Object> map = (Map<String, Object>) result;
        assertInstanceOf(List.class, map.get("a"));
    }

    // ---- Serialization -------------------------------------------------

    @Test
    void writePojoUsesRenamedFieldsAndSkipsIgnored() {
        User user = new User("Ada", 36, Role.USER, List.of("x"), "secret");
        String out = json.write(user);

        assertTrue(out.contains("\"first_name\":\"Ada\""), out);
        assertTrue(out.contains("\"role\":\"USER\""), out);
        assertFalse(out.contains("passwordHash"), "ignored field leaked: " + out);
        assertFalse(out.contains("secret"), out);
    }

    @Test
    void writeRecord() {
        assertEquals("{\"x\":1,\"y\":2,\"label\":\"o\"}",
                json.write(new Point(1, 2, "o")));
    }

    @Test
    void roundTripPojo() {
        User original = new User("Grace", 40, Role.ADMIN, List.of("a", "b"), "pw");
        String text = json.write(original);
        User restored = json.read(stream(text), User.class);

        assertEquals(original.firstName, restored.firstName);
        assertEquals(original.age, restored.age);
        assertEquals(original.role, restored.role);
        assertEquals(original.tags, restored.tags);
    }

    // ---- Pretty print config ------------------------------------------

    @Test
    void prettyPrintProducesIndentedOutput() {
        Json pretty = Json.create(JsonConfig.builder().prettyPrint(true).indent("  ").build());
        String out = pretty.write(new Point(1, 2, "o"));
        String expected = """
                {
                  "x": 1,
                  "y": 2,
                  "label": "o"
                }""";
        assertEquals(expected, out);
    }

    @Test
    void serializeNullsFalseOmitsNullProperties() {
        Json noNulls = Json.create(JsonConfig.builder().serializeNulls(false).build());
        User user = new User(null, 1, null, null, null);
        String out = noNulls.write(user);
        assertFalse(out.contains("first_name"), out);
        assertFalse(out.contains("role"), out);
        assertTrue(out.contains("\"age\":1"), out);
    }

    @Test
    void serializeNullsTrueByDefault() {
        User user = new User(null, 1, null, null, null);
        String out = json.write(user);
        assertTrue(out.contains("\"first_name\":null"), out);
    }

    // ---- Parser edge cases & errors -----------------------------------

    @Test
    void parsesEscapesAndUnicode() {
        String s = json.read("\"line\\nbreak \\u0041\"", String.class);
        assertEquals("line\nbreak A", s);
    }

    @Test
    void parsesNumbersWithExponentsAndNegatives() {
        Object result = json.read("{\"v\": -1.5e3}", Object.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        assertEquals(new java.math.BigDecimal("-1.5e3"), map.get("v"));
    }

    @Test
    void rejectsTrailingGarbage() {
        assertThrows(JsonException.class, () -> json.read("{} junk", Object.class));
    }

    @Test
    void rejectsLeadingZeros() {
        assertThrows(JsonException.class, () -> json.read("01", Object.class));
    }

    @Test
    void rejectsUnterminatedString() {
        assertThrows(JsonException.class, () -> json.read("\"abc", String.class));
    }

    @Test
    void readsEmptyObjectAndArray() {
        assertTrue(json.read("{}", Map.class).isEmpty());
        assertTrue(json.read("[]", List.class).isEmpty());
    }

    // ---- BigDecimal / BigInteger / char fields ------------------------

    @Test
    void serializesBigDecimalBigIntegerAndCharFields() {
        Numbers numbers = new Numbers(new BigDecimal("3.14159"),
                new BigInteger("123456789012345678901234567890"), 'A');
        String out = json.write(numbers);

        assertTrue(out.contains("\"decimal\":3.14159"), out);
        assertTrue(out.contains("\"integer\":123456789012345678901234567890"), out);
        assertTrue(out.contains("\"grade\":\"A\""), out);
    }

    @Test
    void roundTripsBigDecimalBigIntegerAndCharFields() {
        Numbers original = new Numbers(new BigDecimal("2.5"),
                new BigInteger("99999999999999999999"), 'Z');
        Numbers restored = json.read(json.write(original), Numbers.class);

        assertEquals(original.decimal, restored.decimal);
        assertEquals(original.integer, restored.integer);
        assertEquals(original.grade, restored.grade);
    }

    // ---- readTree / write(Writer) -------------------------------------

    @Test
    void readTreeFromInputStreamReturnsRawTree() {
        JsonValue tree = json.readTree(stream("{\"a\":1,\"b\":[true,null]}"));
        assertInstanceOf(JsonValue.JsonObject.class, tree);
        JsonValue.JsonObject object = (JsonValue.JsonObject) tree;
        assertEquals(new JsonValue.JsonNumber(new BigDecimal("1")), object.get("a"));
        assertInstanceOf(JsonValue.JsonArray.class, object.get("b"));
    }

    @Test
    void readTreeFromStringReturnsRawTree() {
        JsonValue tree = json.readTree("[1,2,3]");
        assertInstanceOf(JsonValue.JsonArray.class, tree);
        assertEquals(3, ((JsonValue.JsonArray) tree).size());
    }

    @Test
    void writeToWriterProducesSameOutputAsString() {
        Point point = new Point(7, 8, "w");
        StringWriter writer = new StringWriter();
        json.write(point, writer);
        assertEquals(json.write(point), writer.toString());
    }

    // ---- Collection / Map target subtypes -----------------------------

    @Test
    void readMapPreservesRequestedSubtype() {
        Object result = json.read("{\"b\":2,\"a\":1}", TreeMap.class);
        assertInstanceOf(TreeMap.class, result);
        @SuppressWarnings("unchecked")
        TreeMap<String, Object> map = (TreeMap<String, Object>) result;
        // TreeMap sorts keys: first key must be "a".
        assertEquals("a", map.firstKey());
    }

    @Test
    void readListPreservesRequestedSubtype() {
        Object result = json.read("[1,2,3]", LinkedList.class);
        assertInstanceOf(LinkedList.class, result);
        assertEquals(3, ((LinkedList<?>) result).size());
    }

    @Test
    void readSetTargetProducesSetAndDeduplicates() {
        Object result = json.read("[1,1,2,3,3]", Set.class);
        assertInstanceOf(Set.class, result);
        assertEquals(3, ((Set<?>) result).size());
    }

    @Test
    void readLinkedHashSetPreservesRequestedSubtype() {
        Object result = json.read("[\"x\",\"y\"]", LinkedHashSet.class);
        assertInstanceOf(LinkedHashSet.class, result);
    }

    // ---- Enum errors --------------------------------------------------

    @Test
    void invalidEnumConstantThrowsJsonException() {
        JsonException ex = assertThrows(JsonException.class,
                () -> json.read("\"SUPERUSER\"", Role.class));
        assertTrue(ex.getMessage().contains("SUPERUSER"), ex.getMessage());
    }

    // ---- NaN / Infinity -----------------------------------------------

    @Test
    void serializingNaNThrowsJsonException() {
        assertThrows(JsonException.class, () -> json.write(Double.NaN));
    }

    @Test
    void serializingPositiveInfinityThrowsJsonException() {
        assertThrows(JsonException.class, () -> json.write(Double.POSITIVE_INFINITY));
    }

    @Test
    void serializingNegativeInfinityFloatThrowsJsonException() {
        assertThrows(JsonException.class, () -> json.write(Float.NEGATIVE_INFINITY));
    }

    // ---- BOM handling -------------------------------------------------

    @Test
    void skipsLeadingByteOrderMark() {
        Point point = json.read("\uFEFF{\"x\":1,\"y\":2,\"label\":\"o\"}", Point.class);
        assertEquals(new Point(1, 2, "o"), point);
    }
}
