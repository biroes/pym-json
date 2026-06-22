package com.biroes.pym.json;

import java.util.Objects;

/**
 * Immutable configuration for {@link Json}.
 * <p>
 * Create instances via {@link #defaults()} or the {@link Builder} obtained
 * through {@link #builder()}:
 * <pre>{@code
 * JsonConfig config = JsonConfig.builder()
 *         .prettyPrint(true)
 *         .indent("  ")
 *         .serializeNulls(false)
 *         .build();
 * }</pre>
 */
public final class JsonConfig {

    private final boolean prettyPrint;
    private final String indent;
    private final boolean serializeNulls;

    private JsonConfig(Builder builder) {
        this.prettyPrint = builder.prettyPrint;
        this.indent = builder.indent;
        this.serializeNulls = builder.serializeNulls;
    }

    /**
     * @return {@code true} if output should be indented and spread over multiple lines
     */
    public boolean prettyPrint() {
        return prettyPrint;
    }

    /**
     * @return the indentation unit used per nesting level when {@link #prettyPrint()} is enabled
     */
    public String indent() {
        return indent;
    }

    /**
     * @return {@code true} if {@code null} valued properties are written, {@code false} to omit them
     */
    public boolean serializeNulls() {
        return serializeNulls;
    }

    /**
     * @return the default configuration: compact output, two-space indent, nulls serialized
     */
    public static JsonConfig defaults() {
        return builder().build();
    }

    /**
     * @return a new {@link Builder} initialized with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder for {@link JsonConfig}. */
    public static final class Builder {
        private boolean prettyPrint = false;
        private String indent = "  ";
        private boolean serializeNulls = true;

        private Builder() {
        }

        /**
         * Enables or disables human-readable, indented output.
         *
         * @param prettyPrint whether to pretty-print
         * @return this builder
         */
        public Builder prettyPrint(boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        /**
         * Sets the indentation unit (e.g. {@code "  "} or {@code "\t"}) used when
         * pretty printing.
         *
         * @param indent the indentation string, must not be {@code null}
         * @return this builder
         */
        public Builder indent(String indent) {
            this.indent = Objects.requireNonNull(indent, "indent");
            return this;
        }

        /**
         * Controls whether {@code null} valued object properties are emitted.
         *
         * @param serializeNulls {@code true} to write nulls, {@code false} to omit them
         * @return this builder
         */
        public Builder serializeNulls(boolean serializeNulls) {
            this.serializeNulls = serializeNulls;
            return this;
        }

        /**
         * @return a new immutable {@link JsonConfig}
         */
        public JsonConfig build() {
            return new JsonConfig(this);
        }
    }
}
