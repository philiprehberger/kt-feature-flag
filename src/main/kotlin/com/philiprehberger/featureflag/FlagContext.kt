package com.philiprehberger.featureflag

/**
 * Context for evaluating feature flags, providing user identity and attributes
 * for targeting and percentage rollouts.
 *
 * @property userId optional user identifier for consistent percentage rollouts
 * @property attributes key-value pairs for segment-based targeting
 */
data class FlagContext(
    val userId: String? = null,
    val attributes: Map<String, String> = emptyMap()
) {
    companion object {
        /** An empty context with no user or attributes. */
        val EMPTY = FlagContext()
    }

    /**
     * Builder for constructing a [FlagContext] using DSL syntax.
     */
    class Builder {
        /** The user identifier. */
        var userId: String? = null
        private val attrs = mutableMapOf<String, String>()

        /**
         * Adds an attribute key-value pair for segment targeting.
         *
         * @param key the attribute name
         * @param value the attribute value
         */
        fun attribute(key: String, value: String) {
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
fun flagContext(block: FlagContext.Builder.() -> Unit): FlagContext {
    return FlagContext.Builder().apply(block).build()
}
