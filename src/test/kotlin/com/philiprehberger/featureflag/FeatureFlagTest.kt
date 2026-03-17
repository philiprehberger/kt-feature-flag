package com.philiprehberger.featureflag

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
