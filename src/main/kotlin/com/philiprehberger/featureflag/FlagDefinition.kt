package com.philiprehberger.featureflag

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.absoluteValue

/**
 * Defines how a feature flag is evaluated.
 *
 * This is a sealed interface with three variants:
 * - [BooleanFlag] for simple on/off toggles
 * - [PercentageFlag] for gradual rollouts based on user ID hashing
 * - [SegmentFlag] for attribute-based user targeting
 */
@Serializable
sealed interface FlagDefinition {

    /**
     * Evaluates this flag definition against the given context.
     *
     * @param flagName the name of the flag (used for hashing in percentage rollouts)
     * @param context the evaluation context
     * @return true if the flag is enabled for this context
     */
    fun evaluate(flagName: String, context: FlagContext): Boolean
}

/**
 * A simple boolean toggle flag.
 *
 * @property enabled whether the flag is enabled
 */
@Serializable
@SerialName("boolean")
data class BooleanFlag(val enabled: Boolean) : FlagDefinition {
    override fun evaluate(flagName: String, context: FlagContext): Boolean = enabled
}

/**
 * A percentage-based rollout flag.
 *
 * Uses a deterministic hash of the flag name and user ID to ensure
 * consistent evaluation for the same user across calls.
 *
 * @property percentage the rollout percentage (0-100)
 */
@Serializable
@SerialName("percentage")
data class PercentageFlag(val percentage: Int) : FlagDefinition {
    init {
        require(percentage in 0..100) { "Percentage must be between 0 and 100, got $percentage" }
    }

    override fun evaluate(flagName: String, context: FlagContext): Boolean {
        val userId = context.userId ?: return false
        val hash = "$flagName:$userId".hashCode().absoluteValue
        return (hash % 100) < percentage
    }
}

/**
 * A segment-based targeting flag.
 *
 * Matches when the user's attributes contain at least one matching
 * key-value pair from the enabled segments.
 *
 * @property enabledFor a map of attribute names to lists of accepted values
 */
@Serializable
@SerialName("segment")
data class SegmentFlag(val enabledFor: Map<String, List<String>>) : FlagDefinition {
    override fun evaluate(flagName: String, context: FlagContext): Boolean {
        return enabledFor.any { (key, values) ->
            context.attributes[key] in values
        }
    }
}
