# feature-flag

[![CI](https://github.com/philiprehberger/kt-feature-flag/actions/workflows/publish.yml/badge.svg)](https://github.com/philiprehberger/kt-feature-flag/actions/workflows/publish.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.philiprehberger/feature-flag)](https://central.sonatype.com/artifact/com.philiprehberger/feature-flag)
[![License](https://img.shields.io/github/license/philiprehberger/kt-feature-flag)](LICENSE)

Local feature flag evaluation with percentage rollouts, user targeting, time-based flags, and composable rules.

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.philiprehberger:feature-flag:0.2.3")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.philiprehberger:feature-flag:0.2.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.philiprehberger</groupId>
    <artifactId>feature-flag</artifactId>
    <version>0.2.3</version>
</dependency>
```

## Usage

### Basic Boolean Flags

```kotlin
import com.philiprehberger.featureflag.*

val flags = featureFlags {
    inMemory(mapOf(
        "dark-mode" to BooleanFlag(true),
        "new-checkout" to BooleanFlag(false)
    ))
}

if (flags.isEnabled("dark-mode")) {
    enableDarkMode()
}
```

### Percentage Rollouts

```kotlin
val flags = featureFlags {
    inMemory(mapOf("new-ui" to PercentageFlag(25)))
}

val ctx = flagContext {
    userId = currentUser.id
}

// Deterministic: same user always gets the same result
if (flags.isEnabled("new-ui", ctx)) {
    showNewUI()
}
```

### Segment Targeting

```kotlin
val flags = featureFlags {
    inMemory(mapOf(
        "premium-feature" to SegmentFlag(
            enabledFor = mapOf("plan" to listOf("premium", "enterprise"))
        )
    ))
}

val ctx = flagContext {
    userId = currentUser.id
    attribute("plan", currentUser.plan)
}

flags.isEnabled("premium-feature", ctx) // true for premium/enterprise users
```

### Time-Based Flags

Enable a flag only within a specific time window using `TimeBasedFlag`:

```kotlin
import java.time.Instant

val flags = featureFlags {
    inMemory(mapOf(
        "holiday-promo" to TimeBasedFlag(
            startDate = Instant.parse("2026-12-20T00:00:00Z"),
            endDate = Instant.parse("2026-12-31T23:59:59Z")
        )
    ))
}

// Returns true only between Dec 20 and Dec 31
flags.isEnabled("holiday-promo")
```

Either `startDate` or `endDate` can be omitted for open-ended windows.

### Combining Rules with AND/OR

Use `and` / `or` infix operators to compose flag definitions:

```kotlin
// Both conditions must be true
val premiumInEu = SegmentFlag(
    enabledFor = mapOf("plan" to listOf("premium"))
) and SegmentFlag(
    enabledFor = mapOf("region" to listOf("eu"))
)

// At least one condition must be true
val premiumOrBeta = SegmentFlag(
    enabledFor = mapOf("plan" to listOf("premium"))
) or SegmentFlag(
    enabledFor = mapOf("role" to listOf("beta"))
)

val flags = featureFlags {
    inMemory(mapOf(
        "premium-eu-feature" to premiumInEu,
        "early-access" to premiumOrBeta
    ))
}
```

### Flag Metadata

Attach metadata to any flag definition for documentation and tracking:

```kotlin
val flag = BooleanFlag(true).withMetadata(
    FlagMetadata(
        description = "Enables the new dark mode theme",
        owner = "ui-team",
        createdAt = Instant.parse("2026-01-15T00:00:00Z")
    )
)

println(flag.metadata?.description) // "Enables the new dark mode theme"
println(flag.metadata?.owner)       // "ui-team"
```

### Listing All Flags

Get a snapshot of all defined flags with their current evaluation state:

```kotlin
val allFlags = flags.allFlags()
for (state in allFlags) {
    println("${state.name}: enabled=${state.enabled}")
    state.definition.metadata?.let { meta ->
        println("  description: ${meta.description}")
    }
}
```

### Cached Flag Source

Wrap any `FlagSource` with `CachedFlagSource` to avoid repeated loading:

```kotlin
import java.time.Duration

val flags = featureFlags {
    cachedSource(JsonFileSource("flags.json"), Duration.ofMinutes(5))
}

// First load reads from file, subsequent loads use cache for 5 minutes
flags.isEnabled("my-flag")
```

### Flag Change Listener

Get notified when flag values change after a `reload()`:

```kotlin
flags.addChangeListener { name, oldValue, newValue ->
    println("Flag '$name' changed from $oldValue to $newValue")
}

flags.reload() // Triggers listener for any changed flags
```

### Observing Flag Changes

```kotlin
flags.observe("dark-mode").collect { enabled ->
    updateTheme(enabled)
}

// Later, reload flags from sources
flags.reload()
```

## API

| Class / Function | Description |
|------------------|-------------|
| `featureFlags { }` | DSL builder for creating a `FeatureFlags` instance |
| `FeatureFlags.isEnabled(flag, context)` | Evaluates whether a flag is enabled |
| `FeatureFlags.getValue(flag, default)` | Gets a typed value with a fallback |
| `FeatureFlags.observe(flag)` | Returns a `Flow<Boolean>` for reactive observation |
| `FeatureFlags.reload()` | Reloads all flag definitions from sources |
| `FeatureFlags.allFlags()` | Returns all flags with their current evaluation state |
| `FeatureFlags.addChangeListener(listener)` | Registers a flag change listener |
| `FeatureFlags.removeChangeListener(listener)` | Removes a flag change listener |
| `BooleanFlag` | Simple on/off toggle |
| `PercentageFlag` | Gradual rollout based on user ID hashing |
| `SegmentFlag` | Attribute-based user targeting |
| `TimeBasedFlag` | Time-window-based flag with `startDate`/`endDate` |
| `CompositeFlag` | Combines flag rules with AND/OR operators |
| `FlagMetadata` | Metadata (description, owner, createdAt) for a flag |
| `FlagDefinition.and(other)` | Combines two flags with AND logic |
| `FlagDefinition.or(other)` | Combines two flags with OR logic |
| `FlagDefinition.withMetadata(meta)` | Attaches metadata to a flag definition |
| `FlagContext` | Evaluation context with userId and attributes |
| `flagContext { }` | DSL builder for `FlagContext` |
| `FlagState` | Snapshot of a flag's name, enabled state, and definition |
| `FlagChangeListener` | Functional interface for flag change notifications |
| `InMemorySource` | In-memory flag source |
| `JsonFileSource` | JSON file-backed flag source |
| `CachedFlagSource` | Caching wrapper with configurable TTL |

## Development

```bash
./gradlew test       # Run tests
./gradlew check      # Run all checks
./gradlew build      # Build JAR
```

## License

MIT
