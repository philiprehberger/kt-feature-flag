package com.philiprehberger.featureflag

/**
 * Context for evaluating feature flags, providing user identity and attributes
 * for targeting and percentage rollouts.
 *
 * @property userId optional user identifier for consistent percentage rollouts
 * @property attributes key-value pairs for segment-based targeting
 */
public data class FlagContext(
    public val userId: String? = null,
    public val attributes: Map<String, String> = emptyMap()
) {
    public companion object {
        /** An empty context with no user or attributes. */
        public val EMPTY: FlagContext = FlagContext()
    }

    /**
     * Builder for constructing a [FlagContext] using DSL syntax.
     */
    public class Builder {
        /** The user identifier. */
        public var userId: String? = null
        private val attrs = mutableMapOf<String, String>()

        /**
         * Adds an attribute key-value pair for segment targeting.
         *
         * @param key the attribute name
         * @param value the attribute value
         */
        public fun attribute(key: String, value: String) {
            attrs[key] = value
        }

        internal fun build(): FlagContext = FlagContext(userId, attrs.toMap())
    }
}

/**
 * Creates a [FlagContext] using DSL syntax.
 *
 * ```
 * val ctx = flagContext {
 *     userId = "user-123"
 *     attribute("plan", "premium")
 * }
 * ```
 *
 * @param block the builder configuration
 * @return a configured [FlagContext]
 */
public fun flagContext(block: FlagContext.Builder.() -> Unit): FlagContext {
    return FlagContext.Builder().apply(block).build()
}
