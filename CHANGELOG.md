# Changelog

All notable changes to this library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-03-18

### Added
- `TimeBasedFlag` with `startDate`/`endDate` for time-window-based feature flags
- `CachedFlagSource` wrapper with configurable TTL for caching flag source results
- `and` / `or` infix operators for combining flag rules into composite flags
- `CompositeFlag` and `CompositeOperator` for AND/OR boolean logic on flag definitions
- `FlagMetadata` data class with description, owner, and createdAt fields
- `FlagDefinition.withMetadata()` to attach metadata to any flag definition
- `FeatureFlags.allFlags()` to list all defined flags with their current evaluation state
- `FlagState` data class for flag listing results
- `FlagChangeListener` functional interface with `onFlagChanged(name, oldValue, newValue)`
- `FeatureFlags.addChangeListener()` and `removeChangeListener()` for change notifications
- `FeatureFlagsBuilder.cachedSource()` DSL method for adding cached sources

## [0.1.1] - 2026-03-18

- Fix CI badge and gradlew permissions

## [0.1.0] - 2026-03-17

### Added
- `FeatureFlags` class with `isEnabled()`, `getValue()`, and `observe()` methods
- `BooleanFlag` for simple on/off toggles
- `PercentageFlag` for deterministic gradual rollouts based on user ID hashing
- `SegmentFlag` for attribute-based user targeting
- `FlagContext` with builder DSL for evaluation context
- `InMemorySource` for in-memory flag definitions
- `JsonFileSource` for loading flags from JSON files
- `featureFlags { }` DSL for building instances with multiple sources
- `reload()` for refreshing flags at runtime
- `observe()` flow for reactive flag state observation
