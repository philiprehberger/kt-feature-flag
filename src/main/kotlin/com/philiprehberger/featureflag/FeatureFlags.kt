package com.philiprehberger.featureflag

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Represents the evaluation state of a single flag.
 *
 * @property name the flag name
 * @property enabled whether the flag is currently enabled (evaluated with empty context)
 * @property definition the underlying flag definition
 */
public data class FlagState(
    public val name: String,
    public val enabled: Boolean,
    public val definition: FlagDefinition
)

/**
 * The main entry point for evaluating feature flags.
 *
 * Loads flag definitions from one or more [FlagSource] instances
 * and evaluates them against a [FlagContext].
 *
 * @property sources the list of flag sources (later sources override earlier ones)
 */
public class FeatureFlags internal constructor(
    private val sources: List<FlagSource>
) {
    @PublishedApi internal val flagsFlow = MutableStateFlow(loadAll())

    private val changeListeners = mutableListOf<FlagChangeListener>()

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
    public fun isEnabled(flag: String, context: FlagContext = FlagContext.EMPTY): Boolean {
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
    public inline fun <reified T> getValue(flag: String, default: T): T {
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
    public fun observe(flag: String): Flow<Boolean> {
        return flagsFlow
            .map { flags -> flags[flag]?.evaluate(flag, FlagContext.EMPTY) ?: false }
            .distinctUntilChanged()
    }

    /**
     * Reloads all flag definitions from the configured sources.
     *
     * This triggers emission on any active [observe] flows if values change
     * and notifies registered [FlagChangeListener]s of any changes.
     */
    public fun reload() {
        val oldFlags = flagsFlow.value
        val newFlags = loadAll()
        flagsFlow.value = newFlags

        if (changeListeners.isNotEmpty()) {
            val allNames = (oldFlags.keys + newFlags.keys)
            for (name in allNames) {
                val oldValue = oldFlags[name]?.evaluate(name, FlagContext.EMPTY) ?: false
                val newValue = newFlags[name]?.evaluate(name, FlagContext.EMPTY) ?: false
                if (oldValue != newValue) {
                    for (listener in changeListeners) {
                        listener.onFlagChanged(name, oldValue, newValue)
                    }
                }
            }
        }
    }

    /**
     * Returns a list of all defined flags with their current evaluation state.
     *
     * Each flag is evaluated against [FlagContext.EMPTY] to determine its
     * current enabled state.
     *
     * @return a list of [FlagState] for every defined flag
     */
    public fun allFlags(): List<FlagState> {
        return flagsFlow.value.map { (name, definition) ->
            FlagState(
                name = name,
                enabled = definition.evaluate(name, FlagContext.EMPTY),
                definition = definition
            )
        }
    }

    /**
     * Registers a [FlagChangeListener] that will be notified on [reload]
     * when any flag's evaluation result changes.
     *
     * @param listener the listener to register
     */
    public fun addChangeListener(listener: FlagChangeListener) {
        changeListeners.add(listener)
    }

    /**
     * Removes a previously registered [FlagChangeListener].
     *
     * @param listener the listener to remove
     */
    public fun removeChangeListener(listener: FlagChangeListener) {
        changeListeners.remove(listener)
    }
}
