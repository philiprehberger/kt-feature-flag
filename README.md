# kt-feature-flag

[![CI](https://github.com/philiprehberger/kt-feature-flag/actions/workflows/publish.yml/badge.svg)](https://github.com/philiprehberger/kt-feature-flag/actions/workflows/publish.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.philiprehberger/feature-flag)](https://central.sonatype.com/artifact/com.philiprehberger/feature-flag)

Local feature flag evaluation with percentage rollouts and user targeting.

## Requirements

- Kotlin 1.9+ / Java 17+

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("com.philiprehberger:feature-flag:0.1.0")
}
```

### Gradle (Groovy)

```groovy
dependencies {
    implementation 'com.philiprehberger:feature-flag:0.1.0'
}
```

### Maven

```xml
<dependency>
    <groupId>com.philiprehberger</groupId>
    <artifactId>feature-flag</artifactId>
    <version>0.1.0</version>
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
| `BooleanFlag` | Simple on/off toggle |
| `PercentageFlag` | Gradual rollout based on user ID hashing |
| `SegmentFlag` | Attribute-based user targeting |
| `FlagContext` | Evaluation context with userId and attributes |
| `flagContext { }` | DSL builder for `FlagContext` |
| `InMemorySource` | In-memory flag source |
| `JsonFileSource` | JSON file-backed flag source |

## Development

```bash
./gradlew test       # Run tests
./gradlew check      # Run all checks
./gradlew build      # Build JAR
```

## License

MIT
