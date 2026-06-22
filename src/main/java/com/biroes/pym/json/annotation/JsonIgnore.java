package com.biroes.pym.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field (or record component) to be ignored during both serialization
 * and deserialization.
 * <p>
 * An ignored field is never written to JSON output and any matching JSON property
 * encountered during deserialization is skipped.
 * <pre>{@code
 * public class User {
 *     private String name;
 *     @JsonIgnore
 *     private String passwordHash; // never serialized or read
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
public @interface JsonIgnore {
}
