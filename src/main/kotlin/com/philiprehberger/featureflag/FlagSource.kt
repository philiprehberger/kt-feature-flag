package com.philiprehberger.featureflag

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File

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
