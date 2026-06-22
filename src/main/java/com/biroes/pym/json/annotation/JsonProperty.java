package com.biroes.pym.json.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Maps a Java field (or record component) to a JSON property with a different name.
 * <p>
 * Use this when the JSON property name differs from the Java field name, e.g.:
 * <pre>{@code
 * public class User {
 *     @JsonProperty("first_name")
 *     private String firstName;
 * }
 * }</pre>
 * The field {@code firstName} will be serialized as {@code "first_name"} and read
 * back from a JSON property named {@code "first_name"}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
public @interface JsonProperty {

    /**
     * The JSON property name to use for this field.
     *
     * @return the JSON property name
     */
    String value();
}
