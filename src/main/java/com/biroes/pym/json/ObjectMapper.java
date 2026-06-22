package com.biroes.pym.json;

import com.biroes.pym.json.annotation.JsonIgnore;
import com.biroes.pym.json.annotation.JsonProperty;
import com.biroes.pym.json.model.JsonValue;
import com.biroes.pym.json.model.JsonValue.JsonArray;
import com.biroes.pym.json.model.JsonValue.JsonBoolean;
import com.biroes.pym.json.model.JsonValue.JsonNull;
import com.biroes.pym.json.model.JsonValue.JsonNumber;
import com.biroes.pym.json.model.JsonValue.JsonObject;
import com.biroes.pym.json.model.JsonValue.JsonString;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges arbitrary Java objects (POJOs, records, collections, maps, primitives)
 * and the {@link JsonValue} tree using reflection.
 * <p>
 * Supported types:
 * <ul>
 *     <li>{@code null}</li>
 *     <li>primitives and their wrappers, {@link String}, {@link CharSequence}</li>
 *     <li>{@link BigInteger}, {@link BigDecimal}, {@link Number}</li>
 *     <li>{@link Enum} (mapped to/from its {@link Enum#name() name})</li>
 *     <li>{@link Collection} and Java arrays (mapped to JSON arrays)</li>
 *     <li>{@link Map} with string-convertible keys (mapped to JSON objects)</li>
 *     <li>records (deserialized via their canonical constructor)</li>
 *     <li>plain POJOs with an accessible no-argument constructor</li>
 * </ul>
 * Honours {@link JsonProperty} (custom name) and {@link JsonIgnore} (skip field).
 */
final class ObjectMapper {

    /**
     * Cache of the reflective field metadata used during (de)serialization.
     * Computing the serializable fields, resolving their JSON names and making
     * them accessible is comparatively expensive, so the result is memoized per
     * class. {@link ConcurrentHashMap} keeps the mapper thread safe.
     */
    private static final Map<Class<?>, List<FieldMetadata>> FIELD_CACHE = new ConcurrentHashMap<>();

    /** A serializable field together with its pre-resolved JSON property name. */
    private record FieldMetadata(Field field, String jsonName) {
    }

    JsonValue toJson(Object value) {
        return switch (value) {
            case null -> JsonNull.INSTANCE;
            case JsonValue jv -> jv;
            case String s -> new JsonString(s);
            case Character c -> new JsonString(c.toString());
            case Boolean b -> JsonBoolean.of(b);
            case BigDecimal d -> new JsonNumber(d);
            case BigInteger i -> new JsonNumber(new BigDecimal(i));
            case Number n -> new JsonNumber(numberToBigDecimal(n));
            case Enum<?> e -> new JsonString(e.name());
            case CharSequence cs -> new JsonString(cs.toString());
            case Map<?, ?> map -> mapToJson(map);
            case Collection<?> collection -> collectionToJson(collection);
            default -> {
                if (value.getClass().isArray()) {
                    yield arrayToJson(value);
                }
                yield beanToJson(value);
            }
        };
    }

    private static BigDecimal numberToBigDecimal(Number n) {
        return switch (n) {
            case Byte b -> BigDecimal.valueOf(b.longValue());
            case Short s -> BigDecimal.valueOf(s.longValue());
            case Integer i -> BigDecimal.valueOf(i.longValue());
            case Long l -> BigDecimal.valueOf(l);
            case Float f -> floatingPointToBigDecimal(f.doubleValue());
            case Double d -> floatingPointToBigDecimal(d);
            default -> new BigDecimal(n.toString());
        };
    }

    private static BigDecimal floatingPointToBigDecimal(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new JsonException("Cannot serialize non-finite number " + value
                    + " to JSON (NaN and Infinity are not permitted by the JSON specification)");
        }
        return BigDecimal.valueOf(value);
    }

    private JsonValue mapToJson(Map<?, ?> map) {
        JsonObject object = new JsonObject();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (key == null) {
                throw new JsonException("Map keys must not be null when serializing to JSON");
            }
            object.put(key.toString(), toJson(entry.getValue()));
        }
        return object;
    }

    private JsonValue collectionToJson(Collection<?> collection) {
        JsonArray array = new JsonArray();
        for (Object element : collection) {
            array.add(toJson(element));
        }
        return array;
    }

    private JsonValue arrayToJson(Object array) {
        JsonArray result = new JsonArray();
        int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            result.add(toJson(Array.get(array, i)));
        }
        return result;
    }

    private JsonValue beanToJson(Object bean) {
        Class<?> type = bean.getClass();
        if (type.isRecord()) {
            return recordToJson(bean, type);
        }
        JsonObject object = new JsonObject();
        for (FieldMetadata meta : serializableFields(type)) {
            Field field = meta.field();
            try {
                object.put(meta.jsonName(), toJson(field.get(bean)));
            } catch (IllegalAccessException e) {
                throw new JsonException("Cannot read field '" + field.getName()
                        + "' of " + type.getName(), e);
            }
        }
        return object;
    }

    private JsonValue recordToJson(Object record, Class<?> type) {
        JsonObject object = new JsonObject();
        for (RecordComponent component : type.getRecordComponents()) {
            if (component.isAnnotationPresent(JsonIgnore.class)) {
                continue;
            }
            try {
                Object value = component.getAccessor().invoke(record);
                object.put(jsonName(component, component.getName()), toJson(value));
            } catch (ReflectiveOperationException e) {
                throw new JsonException("Cannot read record component '"
                        + component.getName() + "' of " + type.getName(), e);
            }
        }
        return object;
    }

    // ------------------------------------------------------------------
    // Deserialization
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    <T> T fromJson(JsonValue value, Class<T> type) {
        return (T) convert(value, type, null);
    }

    private Object convert(JsonValue value, Class<?> type, Type genericType) {
        if (value instanceof JsonNull) {
            return type.isPrimitive()
                    ? primitiveDefault(type)
                    : null;
        }
        // Scalars
        if (type == String.class || type == CharSequence.class) {
            return asString(value);
        }
        if (type == boolean.class || type == Boolean.class) {
            return asBoolean(value);
        }
        if (type == char.class || type == Character.class) {
            String s = asString(value);
            if (s.length() != 1) {
                throw new JsonException("Cannot convert string of length "
                        + s.length() + " to char");
            }
            return s.charAt(0);
        }
        if (Number.class.isAssignableFrom(box(type)) || type.isPrimitive()) {
            return asNumber(value, type);
        }
        if (type.isEnum()) {
            return asEnum(value, type);
        }
        if (Map.class.isAssignableFrom(type)) {
            return asMap(value, type, genericType);
        }
        if (Collection.class.isAssignableFrom(type)) {
            return asCollection(value, type, genericType);
        }
        if (type.isArray()) {
            return asArray(value, type);
        }
        if (type == Object.class) {
            return asGenericObject(value);
        }
        if (type.isRecord()) {
            return asRecord(value, type);
        }
        return asBean(value, type);
    }

    private static Object primitiveDefault(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private String asString(JsonValue value) {
        return switch (value) {
            case JsonString s -> s.value();
            case JsonNumber n -> n.value().toString();
            case JsonBoolean b -> Boolean.toString(b.value());
            default -> throw typeError(value, "string");
        };
    }

    private boolean asBoolean(JsonValue value) {
        if (value instanceof JsonBoolean b) {
            return b.value();
        }
        throw typeError(value, "boolean");
    }

    private Object asNumber(JsonValue value, Class<?> type) {
        if (!(value instanceof JsonNumber n)) {
            throw typeError(value, "number");
        }
        BigDecimal d = n.value();
        Class<?> boxed = box(type);
        if (boxed == Byte.class) return d.byteValueExact();
        if (boxed == Short.class) return d.shortValueExact();
        if (boxed == Integer.class) return d.intValueExact();
        if (boxed == Long.class) return d.longValueExact();
        if (boxed == Float.class) return d.floatValue();
        if (boxed == Double.class) return d.doubleValue();
        if (boxed == BigInteger.class) return d.toBigIntegerExact();
        if (boxed == BigDecimal.class) return d;
        if (boxed == Number.class) return d;
        throw new JsonException("Unsupported numeric target type " + type.getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object asEnum(JsonValue value, Class<?> type) {
        String name = asString(value);
        try {
            return Enum.valueOf((Class<? extends Enum>) type, name);
        } catch (IllegalArgumentException e) {
            throw new JsonException("'" + name + "' is not a valid constant of enum "
                    + type.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object asMap(JsonValue value, Class<?> type, Type genericType) {
        if (!(value instanceof JsonObject object)) {
            throw typeError(value, "object");
        }
        Class<?> valueType = typeArgument(genericType, 1);
        Map<String, Object> map = (Map<String, Object>) newMap(type);
        for (Map.Entry<String, JsonValue> entry : object.members().entrySet()) {
            map.put(entry.getKey(), convert(entry.getValue(), valueType,
                    typeArgumentGeneric(genericType, 1)));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Object asCollection(JsonValue value, Class<?> type, Type genericType) {
        if (!(value instanceof JsonArray array)) {
            throw typeError(value, "array");
        }
        Collection<Object> collection = (Collection<Object>) newCollection(type);
        Class<?> elementType = typeArgument(genericType, 0);
        Type elementGeneric = typeArgumentGeneric(genericType, 0);
        for (JsonValue element : array.elements()) {
            collection.add(convert(element, elementType, elementGeneric));
        }
        return collection;
    }

    /**
     * Instantiates a concrete {@link Map} for the requested {@code type}. If the
     * type is an interface or abstract (e.g. {@code Map.class}), a
     * {@link LinkedHashMap} is used to preserve insertion order; otherwise the
     * requested concrete class is instantiated via its no-argument constructor.
     */
    private static Map<?, ?> newMap(Class<?> type) {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            return new LinkedHashMap<>();
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (Map<?, ?>) constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new JsonException("Cannot instantiate map type " + type.getName(), e);
        }
    }

    /**
     * Instantiates a concrete {@link Collection} for the requested {@code type}.
     * If the type is an interface or abstract, a {@link LinkedHashSet} is used for
     * {@link Set} targets and an {@link ArrayList} otherwise; concrete classes are
     * instantiated via their no-argument constructor.
     */
    private static Collection<?> newCollection(Class<?> type) {
        if (type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            return Set.class.isAssignableFrom(type) ? new LinkedHashSet<>() : new ArrayList<>();
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (Collection<?>) constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new JsonException("Cannot instantiate collection type " + type.getName(), e);
        }
    }

    private Object asArray(JsonValue value, Class<?> type) {
        if (!(value instanceof JsonArray array)) {
            throw typeError(value, "array");
        }
        Class<?> componentType = type.getComponentType();
        Object result = Array.newInstance(componentType, array.size());
        for (int i = 0; i < array.size(); i++) {
            Array.set(result, i, convert(array.get(i), componentType, componentType));
        }
        return result;
    }

    private Object asGenericObject(JsonValue value) {
        return switch (value) {
            case JsonNull ignored -> null;
            case JsonString s -> s.value();
            case JsonBoolean b -> b.value();
            case JsonNumber n -> n.value();
            case JsonObject object -> {
                Map<String, Object> map = new LinkedHashMap<>();
                for (var entry : object.members().entrySet()) {
                    map.put(entry.getKey(), asGenericObject(entry.getValue()));
                }
                yield map;
            }
            case JsonArray array -> {
                List<Object> list = new ArrayList<>();
                for (JsonValue element : array.elements()) {
                    list.add(asGenericObject(element));
                }
                yield list;
            }
        };
    }

    private Object asRecord(JsonValue value, Class<?> type) {
        if (!(value instanceof JsonObject object)) {
            throw typeError(value, "object");
        }
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            paramTypes[i] = component.getType();
            if (component.isAnnotationPresent(JsonIgnore.class)) {
                args[i] = primitiveDefault(component.getType());
                continue;
            }
            String name = jsonName(component, component.getName());
            JsonValue member = object.has(name) ? object.get(name) : JsonNull.INSTANCE;
            args[i] = convert(member, component.getType(), component.getGenericType());
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(paramTypes);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new JsonException("Cannot instantiate record " + type.getName(), e);
        }
    }

    private Object asBean(JsonValue value, Class<?> type) {
        if (!(value instanceof JsonObject object)) {
            throw typeError(value, "object");
        }
        Object instance = newInstance(type);
        for (FieldMetadata meta : serializableFields(type)) {
            Field field = meta.field();
            String name = meta.jsonName();
            if (!object.has(name)) {
                continue;
            }
            try {
                Object converted = convert(object.get(name), field.getType(),
                        field.getGenericType());
                field.set(instance, converted);
            } catch (IllegalAccessException e) {
                throw new JsonException("Cannot set field '" + field.getName()
                        + "' of " + type.getName(), e);
            }
        }
        return instance;
    }

    private Object newInstance(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new JsonException("Type " + type.getName()
                    + " has no accessible no-argument constructor", e);
        } catch (ReflectiveOperationException e) {
            throw new JsonException("Cannot instantiate " + type.getName(), e);
        }
    }

    // ------------------------------------------------------------------
    // Reflection helpers
    // ------------------------------------------------------------------

    private static List<FieldMetadata> serializableFields(Class<?> type) {
        return FIELD_CACHE.computeIfAbsent(type, ObjectMapper::computeSerializableFields);
    }

    private static List<FieldMetadata> computeSerializableFields(Class<?> type) {
        List<FieldMetadata> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers) || Modifier.isTransient(modifiers)
                        || field.isSynthetic()) {
                    continue;
                }
                if (field.isAnnotationPresent(JsonIgnore.class)) {
                    continue;
                }
                field.setAccessible(true);
                fields.add(new FieldMetadata(field, jsonName(field, field.getName())));
            }
        }
        return List.copyOf(fields);
    }

    private static String jsonName(AnnotatedElement element, String defaultName) {
        JsonProperty property = element.getAnnotation(JsonProperty.class);
        if (property != null && !property.value().isEmpty()) {
            return property.value();
        }
        return defaultName;
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return type;
    }

    private static Class<?> typeArgument(Type genericType, int index) {
        Type arg = typeArgumentGeneric(genericType, index);
        return switch (arg) {
            case Class<?> c -> c;
            case ParameterizedType pt when pt.getRawType() instanceof Class<?> raw -> raw;
            case null, default -> Object.class;
        };
    }

    private static Type typeArgumentGeneric(Type genericType, int index) {
        if (genericType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (index < args.length) {
                return args[index];
            }
        }
        return Object.class;
    }

    private static JsonException typeError(JsonValue value, String expected) {
        return new JsonException("Expected a JSON " + expected + " but found "
                + value.getClass().getSimpleName());
    }
}
