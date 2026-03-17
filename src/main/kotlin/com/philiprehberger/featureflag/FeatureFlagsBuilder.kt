package com.philiprehberger.featureflag

/**
 * DSL builder for creating a [FeatureFlags] instance.
 *
 * ```
 * val flags = featureFlags {
 *     source(InMemorySource(mapOf("dark-mode" to BooleanFlag(true))))
 * }
 * ```
 */
class FeatureFlagsBuilder {
    private val sources = mutableListOf<FlagSource>()

    /**
     * Adds a [FlagSource] to the configuration.
     * Sources added later take priority over earlier ones.
     *
     * @param source the flag source to add
     */
    fun source(source: FlagSource) {
        sources.add(source)
    }

    /**
     * Adds an [InMemorySource] with the given flag map.
     *
     * @param flags the map of flag names to definitions
     */
    fun inMemory(flags: Map<String, FlagDefinition>) {
        sources.add(InMemorySource(flags))
    }

    /**
     * Adds a [JsonFileSource] that reads from the given path.
     *
     * @param path the file path to the JSON file
     */
    fun jsonFile(path: String) {
        sources.add(JsonFileSource(path))
    }

    internal fun build(): FeatureFlags {
        return FeatureFlags(sources.toList())
    }
}

/**
 * Creates a [FeatureFlags] instance using DSL syntax.
 *
 * ```
 * val flags = featureFlags {
 *     inMemory(mapOf(
 *         "dark-mode" to BooleanFlag(true),
 *         "new-ui" to PercentageFlag(50)
 *     ))
 * }
 * ```
 *
 * @param block the builder configuration
 * @return a configured [FeatureFlags] instance
 */
fun featureFlags(block: FeatureFlagsBuilder.() -> Unit): FeatureFlags {
    return FeatureFlagsBuilder().apply(block).build()
}
