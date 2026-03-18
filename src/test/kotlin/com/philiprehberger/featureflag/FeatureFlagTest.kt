package com.philiprehberger.featureflag

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureFlagTest {

    @Test
    fun `boolean flag returns enabled state`() {
        val flags = featureFlags {
            inMemory(mapOf("dark-mode" to BooleanFlag(true)))
        }

        assertTrue(flags.isEnabled("dark-mode"))
    }

    @Test
    fun `boolean flag returns disabled state`() {
        val flags = featureFlags {
            inMemory(mapOf("dark-mode" to BooleanFlag(false)))
        }

        assertFalse(flags.isEnabled("dark-mode"))
    }

    @Test
    fun `unknown flag returns false`() {
        val flags = featureFlags {
            inMemory(emptyMap())
        }

        assertFalse(flags.isEnabled("nonexistent"))
    }

    @Test
    fun `percentage rollout is deterministic for same user`() {
        val flags = featureFlags {
            inMemory(mapOf("new-ui" to PercentageFlag(50)))
        }

        val ctx = flagContext { userId = "user-42" }
        val first = flags.isEnabled("new-ui", ctx)
        val second = flags.isEnabled("new-ui", ctx)
        val third = flags.isEnabled("new-ui", ctx)

        assertEquals(first, second)
        assertEquals(second, third)
    }

    @Test
    fun `percentage rollout without userId returns false`() {
        val flags = featureFlags {
            inMemory(mapOf("new-ui" to PercentageFlag(50)))
        }

        assertFalse(flags.isEnabled("new-ui"))
    }

    @Test
    fun `100 percent rollout always enabled`() {
        val flags = featureFlags {
            inMemory(mapOf("feature" to PercentageFlag(100)))
        }

        val ctx = flagContext { userId = "any-user" }
        assertTrue(flags.isEnabled("feature", ctx))
    }

    @Test
    fun `0 percent rollout always disabled`() {
        val flags = featureFlags {
            inMemory(mapOf("feature" to PercentageFlag(0)))
        }

        val ctx = flagContext { userId = "any-user" }
        assertFalse(flags.isEnabled("feature", ctx))
    }

    @Test
    fun `segment flag matches user attributes`() {
        val flags = featureFlags {
            inMemory(
                mapOf(
                    "premium-feature" to SegmentFlag(
                        enabledFor = mapOf("plan" to listOf("premium", "enterprise"))
                    )
                )
            )
        }

        val premiumUser = flagContext {
            userId = "user-1"
            attribute("plan", "premium")
        }
        val freeUser = flagContext {
            userId = "user-2"
            attribute("plan", "free")
        }

        assertTrue(flags.isEnabled("premium-feature", premiumUser))
        assertFalse(flags.isEnabled("premium-feature", freeUser))
    }

    @Test
    fun `segment flag with no matching attributes returns false`() {
        val flags = featureFlags {
            inMemory(
                mapOf(
                    "beta" to SegmentFlag(enabledFor = mapOf("role" to listOf("tester")))
                )
            )
        }

        val ctx = flagContext { userId = "user-1" }
        assertFalse(flags.isEnabled("beta", ctx))
    }

    @Test
    fun `getValue returns default for missing flag`() {
        val flags = featureFlags {
            inMemory(emptyMap())
        }

        assertEquals(42, flags.getValue("missing", 42))
    }

    @Test
    fun `getValue returns boolean for BooleanFlag`() {
        val flags = featureFlags {
            inMemory(mapOf("dark-mode" to BooleanFlag(true)))
        }

        assertEquals(true, flags.getValue("dark-mode", false))
    }

    @Test
    fun `in-memory source provides flags`() {
        val source = InMemorySource(
            mapOf(
                "flag-a" to BooleanFlag(true),
                "flag-b" to BooleanFlag(false)
            )
        )

        val loaded = source.load()
        assertEquals(2, loaded.size)
        assertEquals(BooleanFlag(true), loaded["flag-a"])
    }

    @Test
    fun `observe flow emits current state`() = runTest {
        val flags = featureFlags {
            inMemory(mapOf("feature" to BooleanFlag(true)))
        }

        val value = flags.observe("feature").first()
        assertTrue(value)
    }

    @Test
    fun `later sources override earlier sources`() {
        val flags = featureFlags {
            source(InMemorySource(mapOf("flag" to BooleanFlag(false))))
            source(InMemorySource(mapOf("flag" to BooleanFlag(true))))
        }

        assertTrue(flags.isEnabled("flag"))
    }

    @Test
    fun `flagContext DSL builds correctly`() {
        val ctx = flagContext {
            userId = "user-123"
            attribute("plan", "premium")
            attribute("region", "eu")
        }

        assertEquals("user-123", ctx.userId)
        assertEquals("premium", ctx.attributes["plan"])
        assertEquals("eu", ctx.attributes["region"])
    }

    // --- Time-based flag tests ---

    @Test
    fun `time-based flag enabled during window`() {
        val now = Instant.parse("2026-03-18T12:00:00Z")
        val flag = TimeBasedFlag(
            startDate = Instant.parse("2026-03-18T00:00:00Z"),
            endDate = Instant.parse("2026-03-19T00:00:00Z"),
            clock = { now }
        )
        val flags = featureFlags {
            inMemory(mapOf("promo" to flag))
        }

        assertTrue(flags.isEnabled("promo"))
    }

    @Test
    fun `time-based flag disabled before start`() {
        val now = Instant.parse("2026-03-17T23:59:59Z")
        val flag = TimeBasedFlag(
            startDate = Instant.parse("2026-03-18T00:00:00Z"),
            endDate = Instant.parse("2026-03-19T00:00:00Z"),
            clock = { now }
        )
        val flags = featureFlags {
            inMemory(mapOf("promo" to flag))
        }

        assertFalse(flags.isEnabled("promo"))
    }

    @Test
    fun `time-based flag disabled after end`() {
        val now = Instant.parse("2026-03-19T00:00:00Z")
        val flag = TimeBasedFlag(
            startDate = Instant.parse("2026-03-18T00:00:00Z"),
            endDate = Instant.parse("2026-03-19T00:00:00Z"),
            clock = { now }
        )
        val flags = featureFlags {
            inMemory(mapOf("promo" to flag))
        }

        assertFalse(flags.isEnabled("promo"))
    }

    @Test
    fun `time-based flag with only start date`() {
        val now = Instant.parse("2026-03-20T00:00:00Z")
        val flag = TimeBasedFlag(
            startDate = Instant.parse("2026-03-18T00:00:00Z"),
            clock = { now }
        )
        val flags = featureFlags {
            inMemory(mapOf("feature" to flag))
        }

        assertTrue(flags.isEnabled("feature"))
    }

    @Test
    fun `time-based flag with only end date`() {
        val beforeEnd = Instant.parse("2026-03-17T00:00:00Z")
        val flag = TimeBasedFlag(
            endDate = Instant.parse("2026-03-18T00:00:00Z"),
            clock = { beforeEnd }
        )
        val flags = featureFlags {
            inMemory(mapOf("feature" to flag))
        }

        assertTrue(flags.isEnabled("feature"))
    }

    // --- Cached flag source tests ---

    @Test
    fun `cached source returns cached result within TTL`() {
        var loadCount = 0
        val delegate = object : FlagSource {
            override fun load(): Map<String, FlagDefinition> {
                loadCount++
                return mapOf("flag" to BooleanFlag(true))
            }
        }

        var currentTime = Instant.parse("2026-03-18T00:00:00Z")
        val cached = CachedFlagSource(delegate, Duration.ofMinutes(5)) { currentTime }

        cached.load()
        cached.load()
        cached.load()

        assertEquals(1, loadCount, "Delegate should only be called once within TTL")
    }

    @Test
    fun `cached source refreshes after TTL expires`() {
        var loadCount = 0
        val delegate = object : FlagSource {
            override fun load(): Map<String, FlagDefinition> {
                loadCount++
                return mapOf("flag" to BooleanFlag(true))
            }
        }

        var currentTime = Instant.parse("2026-03-18T00:00:00Z")
        val cached = CachedFlagSource(delegate, Duration.ofMinutes(5)) { currentTime }

        cached.load()
        assertEquals(1, loadCount)

        // Advance past TTL
        currentTime = currentTime.plus(Duration.ofMinutes(6))
        cached.load()
        assertEquals(2, loadCount, "Delegate should be called again after TTL expires")
    }

    @Test
    fun `cached source invalidate forces reload`() {
        var loadCount = 0
        val delegate = object : FlagSource {
            override fun load(): Map<String, FlagDefinition> {
                loadCount++
                return mapOf("flag" to BooleanFlag(true))
            }
        }

        val cached = CachedFlagSource(delegate, Duration.ofMinutes(5))
        cached.load()
        assertEquals(1, loadCount)

        cached.invalidate()
        cached.load()
        assertEquals(2, loadCount, "Delegate should be called again after invalidation")
    }

    // --- Composite flag (AND/OR) tests ---

    @Test
    fun `AND composite flag requires all rules to match`() {
        val premiumSegment = SegmentFlag(enabledFor = mapOf("plan" to listOf("premium")))
        val euSegment = SegmentFlag(enabledFor = mapOf("region" to listOf("eu")))
        val combined = premiumSegment and euSegment

        val premiumEu = flagContext {
            attribute("plan", "premium")
            attribute("region", "eu")
        }
        val premiumUs = flagContext {
            attribute("plan", "premium")
            attribute("region", "us")
        }
        val freeEu = flagContext {
            attribute("plan", "free")
            attribute("region", "eu")
        }

        assertTrue(combined.evaluate("test", premiumEu))
        assertFalse(combined.evaluate("test", premiumUs))
        assertFalse(combined.evaluate("test", freeEu))
    }

    @Test
    fun `OR composite flag requires at least one rule to match`() {
        val premiumSegment = SegmentFlag(enabledFor = mapOf("plan" to listOf("premium")))
        val betaSegment = SegmentFlag(enabledFor = mapOf("role" to listOf("beta")))
        val combined = premiumSegment or betaSegment

        val premiumUser = flagContext { attribute("plan", "premium") }
        val betaUser = flagContext { attribute("role", "beta") }
        val freeUser = flagContext { attribute("plan", "free") }

        assertTrue(combined.evaluate("test", premiumUser))
        assertTrue(combined.evaluate("test", betaUser))
        assertFalse(combined.evaluate("test", freeUser))
    }

    @Test
    fun `AND with boolean and segment flags`() {
        val enabled = BooleanFlag(true)
        val segment = SegmentFlag(enabledFor = mapOf("plan" to listOf("premium")))
        val combined = enabled and segment

        val premiumCtx = flagContext { attribute("plan", "premium") }
        val freeCtx = flagContext { attribute("plan", "free") }

        assertTrue(combined.evaluate("test", premiumCtx))
        assertFalse(combined.evaluate("test", freeCtx))
    }

    @Test
    fun `AND with disabled boolean flag short-circuits`() {
        val disabled = BooleanFlag(false)
        val segment = SegmentFlag(enabledFor = mapOf("plan" to listOf("premium")))
        val combined = disabled and segment

        val premiumCtx = flagContext { attribute("plan", "premium") }
        assertFalse(combined.evaluate("test", premiumCtx))
    }

    @Test
    fun `composite flag used in FeatureFlags evaluation`() {
        val combined = SegmentFlag(
            enabledFor = mapOf("plan" to listOf("premium"))
        ) and SegmentFlag(
            enabledFor = mapOf("region" to listOf("eu"))
        )

        val flags = featureFlags {
            inMemory(mapOf("premium-eu" to combined))
        }

        val ctx = flagContext {
            attribute("plan", "premium")
            attribute("region", "eu")
        }
        assertTrue(flags.isEnabled("premium-eu", ctx))
    }

    // --- allFlags() tests ---

    @Test
    fun `allFlags returns all defined flags with evaluation state`() {
        val flags = featureFlags {
            inMemory(
                mapOf(
                    "enabled-flag" to BooleanFlag(true),
                    "disabled-flag" to BooleanFlag(false),
                    "rollout" to PercentageFlag(50)
                )
            )
        }

        val allFlags = flags.allFlags()
        assertEquals(3, allFlags.size)

        val enabledFlag = allFlags.find { it.name == "enabled-flag" }
        assertNotNull(enabledFlag)
        assertTrue(enabledFlag.enabled)

        val disabledFlag = allFlags.find { it.name == "disabled-flag" }
        assertNotNull(disabledFlag)
        assertFalse(disabledFlag.enabled)
    }

    @Test
    fun `allFlags returns empty list when no flags defined`() {
        val flags = featureFlags {
            inMemory(emptyMap())
        }

        assertTrue(flags.allFlags().isEmpty())
    }

    @Test
    fun `allFlags includes flag definitions`() {
        val boolDef = BooleanFlag(true)
        val flags = featureFlags {
            inMemory(mapOf("my-flag" to boolDef))
        }

        val state = flags.allFlags().first()
        assertEquals("my-flag", state.name)
        assertEquals(boolDef, state.definition)
    }

    // --- FlagMetadata tests ---

    @Test
    fun `flag metadata is attached via withMetadata`() {
        val meta = FlagMetadata(
            description = "Enables dark mode",
            owner = "ui-team",
            createdAt = Instant.parse("2026-01-15T00:00:00Z")
        )
        val flag = BooleanFlag(true).withMetadata(meta)

        assertNotNull(flag.metadata)
        assertEquals("Enables dark mode", flag.metadata!!.description)
        assertEquals("ui-team", flag.metadata!!.owner)
        assertEquals(Instant.parse("2026-01-15T00:00:00Z"), flag.metadata!!.createdAt)
    }

    @Test
    fun `flag metadata defaults to null`() {
        val flag = BooleanFlag(true)
        assertNull(flag.metadata)
    }

    @Test
    fun `withMetadata preserves flag behavior`() {
        val meta = FlagMetadata(description = "A rollout")
        val flag = PercentageFlag(100).withMetadata(meta)

        val ctx = flagContext { userId = "user-1" }
        assertTrue(flag.evaluate("test", ctx))
        assertEquals("A rollout", flag.metadata!!.description)
    }

    @Test
    fun `segment flag withMetadata preserves evaluation`() {
        val meta = FlagMetadata(description = "Premium only", owner = "billing")
        val flag = SegmentFlag(enabledFor = mapOf("plan" to listOf("premium"))).withMetadata(meta)

        val premiumCtx = flagContext { attribute("plan", "premium") }
        assertTrue(flag.evaluate("test", premiumCtx))
        assertEquals("billing", flag.metadata!!.owner)
    }

    @Test
    fun `time-based flag withMetadata preserves evaluation`() {
        val now = Instant.parse("2026-03-18T12:00:00Z")
        val meta = FlagMetadata(description = "Promo window")
        val flag = TimeBasedFlag(
            startDate = Instant.parse("2026-03-18T00:00:00Z"),
            endDate = Instant.parse("2026-03-19T00:00:00Z"),
            clock = { now }
        ).withMetadata(meta)

        assertTrue(flag.evaluate("promo", FlagContext.EMPTY))
        assertEquals("Promo window", flag.metadata!!.description)
    }

    @Test
    fun `metadata with optional fields null`() {
        val meta = FlagMetadata(description = "Simple flag")
        assertNull(meta.owner)
        assertNull(meta.createdAt)
    }

    @Test
    fun `allFlags shows metadata on flags`() {
        val meta = FlagMetadata(description = "Dark mode toggle", owner = "ui-team")
        val flags = featureFlags {
            inMemory(mapOf("dark-mode" to BooleanFlag(true).withMetadata(meta)))
        }

        val state = flags.allFlags().first()
        assertNotNull(state.definition.metadata)
        assertEquals("Dark mode toggle", state.definition.metadata!!.description)
    }

    // --- FlagChangeListener tests ---

    @Test
    fun `change listener notified on reload`() {
        var source = InMemorySource(mapOf("flag" to BooleanFlag(true)))
        val flags = featureFlags {
            source(object : FlagSource {
                override fun load() = source.load()
            })
        }

        val changes = mutableListOf<Triple<String, Boolean, Boolean>>()
        flags.addChangeListener { name, oldValue, newValue ->
            changes.add(Triple(name, oldValue, newValue))
        }

        source = InMemorySource(mapOf("flag" to BooleanFlag(false)))
        flags.reload()

        assertEquals(1, changes.size)
        assertEquals(Triple("flag", true, false), changes[0])
    }

    @Test
    fun `change listener not notified when value unchanged`() {
        val source = InMemorySource(mapOf("flag" to BooleanFlag(true)))
        val flags = featureFlags {
            source(object : FlagSource {
                override fun load() = source.load()
            })
        }

        val changes = mutableListOf<Triple<String, Boolean, Boolean>>()
        flags.addChangeListener { name, oldValue, newValue ->
            changes.add(Triple(name, oldValue, newValue))
        }

        flags.reload()

        assertTrue(changes.isEmpty(), "Listener should not be called when value hasn't changed")
    }

    @Test
    fun `removed change listener is not notified`() {
        var source = InMemorySource(mapOf("flag" to BooleanFlag(true)))
        val flags = featureFlags {
            source(object : FlagSource {
                override fun load() = source.load()
            })
        }

        var notified = false
        val listener = FlagChangeListener { _, _, _ -> notified = true }
        flags.addChangeListener(listener)
        flags.removeChangeListener(listener)

        source = InMemorySource(mapOf("flag" to BooleanFlag(false)))
        flags.reload()

        assertFalse(notified)
    }
}
