package com.philiprehberger.featureflag

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import java.time.Duration
import java.time.Instant

/**
 * A source of feature flag definitions.
 *
 * Implementations load flag definitions from various backends
 * (memory, files, remote services, etc.).
 */
interface FlagSource {
    /**
     * Loads all flag definitions from this source.
     *
     * @return a map of flag names to their definitions
     */
    fun load(): Map<String, FlagDefinition>
}

/**
 * An in-memory flag source backed by a pre-built map.
 *
 * @property flags the map of flag names to definitions
 */
class InMemorySource(private val flags: Map<String, FlagDefinition>) : FlagSource {
    override fun load(): Map<String, FlagDefinition> = flags
}

/**
 * A flag source that reads definitions from a JSON file.
 *
 * The JSON format is an object where each key is a flag name and each value
 * is a serialized [FlagDefinition]:
 * ```json
 * {
 *   "dark-mode": { "type": "boolean", "enabled": true },
 *   "new-ui": { "type": "percentage", "percentage": 50 }
 * }
 * ```
 *
 * @property path the file path to the JSON file
 */
class JsonFileSource(private val path: String) : FlagSource {
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): Map<String, FlagDefinition> {
        val content = File(path).readText()
        val jsonObject = json.decodeFromString<JsonObject>(content)
        return jsonObject.mapValues { (_, value) ->
            json.decodeFromJsonElement<FlagDefinition>(value)
        }
    }
}

/**
 * A caching wrapper around another [FlagSource] that stores results
 * for a configurable time-to-live (TTL) duration.
 *
 * When the TTL has not expired, [load] returns the cached results
 * without delegating to the underlying source. After the TTL expires,
 * the next call to [load] fetches fresh data from the delegate.
 *
 * @property delegate the underlying flag source to cache
 * @property ttl the time-to-live for cached results
 * @property clock a function returning the current time (for testing)
 */
class CachedFlagSource(
    private val delegate: FlagSource,
    private val ttl: Duration,
    private val clock: () -> Instant = { Instant.now() }
) : FlagSource {
    @Volatile
    private var cachedResult: Map<String, FlagDefinition>? = null

    @Volatile
    private var cachedAt: Instant? = null

    override fun load(): Map<String, FlagDefinition> {
        val now = clock()
        val cached = cachedResult
        val at = cachedAt

        if (cached != null && at != null && Duration.between(at, now) < ttl) {
            return cached
        }

        val fresh = delegate.load()
        cachedResult = fresh
        cachedAt = now
        return fresh
    }

    /**
     * Clears the cache, forcing the next [load] call to fetch from the delegate.
     */
    fun invalidate() {
        cachedResult = null
        cachedAt = null
    }
}

/**
 * Listener interface for receiving notifications when flag values change.
 *
 * Implement this functional interface and register it with
 * [FeatureFlags.addChangeListener] to be notified whenever a flag's
 * evaluation result changes after a [FeatureFlags.reload].
 */
fun interface FlagChangeListener {
    /**
     * Called when a flag's value changes.
     *
     * @param name the flag name
     * @param oldValue the previous evaluation result
     * @param newValue the new evaluation result
     */
    fun onFlagChanged(name: String, oldValue: Boolean, newValue: Boolean)
}
