# Changelog

All notable changes to this library will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
