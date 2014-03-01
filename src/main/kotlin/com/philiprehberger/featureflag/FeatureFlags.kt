package com.philiprehberger.featureflag

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The main entry point for evaluating feature flags.
 *
 * Loads flag definitions from one or more [FlagSource] instances
 * and evaluates them against a [FlagContext].
 *
 * @property sources the list of flag sources (later sources override earlier ones)
 */
class FeatureFlags internal constructor(
    private val sources: List<FlagSource>
) {
    @PublishedApi internal val flagsFlow = MutableStateFlow(loadAll())

    private fun loadAll(): Map<String, FlagDefinition> {
        val merged = mutableMapOf<String, FlagDefinition>()
        for (source in sources) {
            merged.putAll(source.load())
        }
        return merged
    }

    /**
     * Checks whether the given flag is enabled for the provided context.
     *
     * @param flag the flag name
     * @param context the evaluation context (defaults to [FlagContext.EMPTY])
     * @return true if the flag is enabled, false if disabled or not found
     */
    fun isEnabled(flag: String, context: FlagContext = FlagContext.EMPTY): Boolean {
        val definition = flagsFlow.value[flag] ?: return false
        return definition.evaluate(flag, context)
    }

    /**
     * Gets a typed value for the given flag with a fallback default.
     *
     * For [BooleanFlag], returns the enabled state cast to T.
     * For other flag types, returns the [default] value.
     *
     * @param flag the flag name
     * @param default the fallback value if the flag is not found
     * @return the flag value or the default
     */
    inline fun <reified T> getValue(flag: String, default: T): T {
        val definition = flagsFlow.value[flag] ?: return default
        return when {
            T::class == Boolean::class && definition is BooleanFlag -> definition.enabled as T
            T::class == Int::class && definition is PercentageFlag -> definition.percentage as T
            else -> default
        }
    }

    /**
     * Observes the enabled state of a flag as a [Flow].
     *
     * The flow emits a new value whenever [reload] is called and the
     * flag's evaluation result changes.
     *
     * @param flag the flag name
     * @return a flow of boolean values
     */
    fun observe(flag: String): Flow<Boolean> {
        return flagsFlow
            .map { flags -> flags[flag]?.evaluate(flag, FlagContext.EMPTY) ?: false }
            .distinctUntilChanged()
    }

    /**
     * Reloads all flag definitions from the configured sources.
     *
     * This triggers emission on any active [observe] flows if values change.
     */
    fun reload() {
        flagsFlow.value = loadAll()
    }
}
