package com.philiprehberger.featureflag

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.math.absoluteValue

/**
 * Optional metadata attached to a flag definition.
 *
 * @property description a human-readable description of the flag
 * @property owner the person or team responsible for the flag
 * @property createdAt when the flag was created
 */
data class FlagMetadata(
    val description: String,
    val owner: String? = null,
    val createdAt: Instant? = null
)

/**
 * Defines how a feature flag is evaluated.
 *
 * This is a sealed interface with variants:
 * - [BooleanFlag] for simple on/off toggles
 * - [PercentageFlag] for gradual rollouts based on user ID hashing
 * - [SegmentFlag] for attribute-based user targeting
 * - [TimeBasedFlag] for time-window-based flags
 * - [CompositeFlag] for combining rules with AND/OR operators
 */
@Serializable
sealed interface FlagDefinition {

    /**
     * Optional metadata for this flag definition.
     * Defaults to null for backward compatibility.
     */
    val metadata: FlagMetadata?
        get() = null

    /**
     * Evaluates this flag definition against the given context.
     *
     * @param flagName the name of the flag (used for hashing in percentage rollouts)
     * @param context the evaluation context
     * @return true if the flag is enabled for this context
     */
    fun evaluate(flagName: String, context: FlagContext): Boolean

    /**
     * Returns a copy of this flag definition with the given metadata attached.
     *
     * @param metadata the metadata to attach
     * @return a new flag definition with metadata
     */
    fun withMetadata(metadata: FlagMetadata): FlagDefinition
}

/**
 * A simple boolean toggle flag.
 *
 * @property enabled whether the flag is enabled
 * @property metadata optional flag metadata
 */
@Serializable
@SerialName("boolean")
data class BooleanFlag(
    val enabled: Boolean,
    @kotlinx.serialization.Transient
    override val metadata: FlagMetadata? = null
) : FlagDefinition {
    override fun evaluate(flagName: String, context: FlagContext): Boolean = enabled

    override fun withMetadata(metadata: FlagMetadata): BooleanFlag =
        copy(metadata = metadata)
}

/**
 * A percentage-based rollout flag.
 *
 * Uses a deterministic hash of the flag name and user ID to ensure
 * consistent evaluation for the same user across calls.
 *
 * @property percentage the rollout percentage (0-100)
 * @property metadata optional flag metadata
 */
@Serializable
@SerialName("percentage")
data class PercentageFlag(
    val percentage: Int,
    @kotlinx.serialization.Transient
    override val metadata: FlagMetadata? = null
) : FlagDefinition {
    init {
        require(percentage in 0..100) { "Percentage must be between 0 and 100, got $percentage" }
    }

    override fun evaluate(flagName: String, context: FlagContext): Boolean {
        val userId = context.userId ?: return false
        val hash = "$flagName:$userId".hashCode().absoluteValue
        return (hash % 100) < percentage
    }

    override fun withMetadata(metadata: FlagMetadata): PercentageFlag =
        copy(metadata = metadata)
}

/**
 * A segment-based targeting flag.
 *
 * Matches when the user's attributes contain at least one matching
 * key-value pair from the enabled segments.
 *
 * @property enabledFor a map of attribute names to lists of accepted values
 * @property metadata optional flag metadata
 */
@Serializable
@SerialName("segment")
data class SegmentFlag(
    val enabledFor: Map<String, List<String>>,
    @kotlinx.serialization.Transient
    override val metadata: FlagMetadata? = null
) : FlagDefinition {
    override fun evaluate(flagName: String, context: FlagContext): Boolean {
        return enabledFor.any { (key, values) ->
            context.attributes[key] in values
        }
    }

    override fun withMetadata(metadata: FlagMetadata): SegmentFlag =
        copy(metadata = metadata)
}

/**
 * A time-based flag that is enabled only within a specified time window.
 *
 * @property startDate the start of the enabled window (inclusive), or null for no start bound
 * @property endDate the end of the enabled window (exclusive), or null for no end bound
 * @property metadata optional flag metadata
 */
data class TimeBasedFlag(
    val startDate: Instant? = null,
    val endDate: Instant? = null,
    @kotlinx.serialization.Transient
    override val metadata: FlagMetadata? = null,
    private val clock: () -> Instant = { Instant.now() }
) : FlagDefinition {
    override fun evaluate(flagName: String, context: FlagContext): Boolean {
        val now = clock()
        if (startDate != null && now.isBefore(startDate)) return false
        if (endDate != null && !now.isBefore(endDate)) return false
        return true
    }

    override fun withMetadata(metadata: FlagMetadata): TimeBasedFlag =
        copy(metadata = metadata)
}

/**
 * The boolean operator for combining flag rules.
 */
enum class CompositeOperator {
    /** All rules must evaluate to true. */
    AND,
    /** At least one rule must evaluate to true. */
    OR
}

/**
 * A composite flag that combines multiple flag definitions using AND/OR logic.
 *
 * @property rules the list of flag definitions to combine
 * @property operator the boolean operator (AND or OR)
 * @property metadata optional flag metadata
 */
data class CompositeFlag(
    val rules: List<FlagDefinition>,
    val operator: CompositeOperator,
    @kotlinx.serialization.Transient
    override val metadata: FlagMetadata? = null
) : FlagDefinition {
    override fun evaluate(flagName: String, context: FlagContext): Boolean {
        return when (operator) {
            CompositeOperator.AND -> rules.all { it.evaluate(flagName, context) }
            CompositeOperator.OR -> rules.any { it.evaluate(flagName, context) }
        }
    }

    override fun withMetadata(metadata: FlagMetadata): CompositeFlag =
        copy(metadata = metadata)
}

/**
 * Combines this flag with another using AND logic.
 * Both flags must evaluate to true for the result to be true.
 *
 * @param other the other flag definition
 * @return a [CompositeFlag] combining both with AND
 */
infix fun FlagDefinition.and(other: FlagDefinition): CompositeFlag {
    val existingRules = if (this is CompositeFlag && operator == CompositeOperator.AND) rules else listOf(this)
    val otherRules = if (other is CompositeFlag && other.operator == CompositeOperator.AND) other.rules else listOf(other)
    return CompositeFlag(existingRules + otherRules, CompositeOperator.AND)
}

/**
 * Combines this flag with another using OR logic.
 * At least one flag must evaluate to true for the result to be true.
 *
 * @param other the other flag definition
 * @return a [CompositeFlag] combining both with OR
 */
infix fun FlagDefinition.or(other: FlagDefinition): CompositeFlag {
    val existingRules = if (this is CompositeFlag && operator == CompositeOperator.OR) rules else listOf(this)
    val otherRules = if (other is CompositeFlag && other.operator == CompositeOperator.OR) other.rules else listOf(other)
    return CompositeFlag(existingRules + otherRules, CompositeOperator.OR)
}
