package com.mbk.hayplan.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class ForecastCacheTest {
    @get:Rule val temporary = TemporaryFolder()
    private val clock = MutableClock()

    @Test fun `fresh entry prevents another network call`() = runBlocking {
        val cache = ForecastCache(temporary.root, clock)
        var calls = 0
        cache.get("weather") { calls++; "first" }
        clock.advance(Duration.ofMinutes(59))
        val result = cache.get("weather") { calls++; "second" }
        assertEquals(1, calls)
        assertEquals("first", result.body)
    }

    @Test fun `entry expires exactly at one hour`() = runBlocking {
        val cache = ForecastCache(temporary.root, clock)
        cache.get("weather") { "first" }
        clock.advance(Duration.ofHours(1))
        assertEquals("second", cache.get("weather") { "second" }.body)
    }

    @Test fun `manual refresh bypasses fresh data and replaces persisted content`() = runBlocking {
        val cache = ForecastCache(temporary.root, clock)
        cache.get("weather") { "first" }
        clock.advance(Duration.ofMinutes(1))
        val result = cache.get("weather", forceRefresh = true) { "second" }
        assertEquals(clock.instant(), result.fetchedAt)
        assertEquals("second", ForecastCache(temporary.root, clock).get("weather") { error("No network expected") }.body)
    }

    @Test fun `cache survives a new repository or application process`() = runBlocking {
        ForecastCache(temporary.root, clock).get("weather") { "saved" }
        val reopened = ForecastCache(temporary.root, clock).get("weather") { error("No network expected") }
        assertEquals("saved", reopened.body)
    }

    @Test fun `failed refresh retains content and original fetch time`() = runBlocking {
        val cache = ForecastCache(temporary.root, clock)
        val original = cache.get("weather") { "saved" }
        clock.advance(Duration.ofHours(2))
        val fallback = cache.get("weather", forceRefresh = true) { throw IOException("offline") }
        assertEquals(original.body, fallback.body)
        assertEquals(original.fetchedAt, fallback.fetchedAt)
        assertTrue(fallback.refreshFailed)
        assertFalse(ForecastCache.isFresh(fallback.fetchedAt, clock.instant()))
    }

    @Test fun `failed refresh rejects a cache older than the fallback limit`() = runBlocking {
        val cache = ForecastCache(temporary.root, clock)
        cache.get("weather") { "saved" }
        clock.advance(ForecastCache.MAX_STALE_AGE.plusMinutes(1))
        try {
            cache.get("weather") { throw IOException("offline") }
            fail("Expected an expired fallback to be rejected")
        } catch (_: IOException) { /* expected */ }
    }

    @Test fun `corrupt file is ignored`() = runBlocking {
        ForecastCache(temporary.root, clock).get("weather") { "saved" }
        temporary.root.listFiles()!!.single().writeText("corrupt")
        assertEquals("new", ForecastCache(temporary.root, clock).get("weather") { "new" }.body)
    }

    @Test fun `invalid response cannot replace valid cached data`() = runBlocking {
        val cache = ForecastCache(temporary.root, clock)
        val validate: (String) -> Unit = { require(it == "valid") }
        cache.get("weather", validate = validate) { "valid" }
        val result = cache.get("weather", forceRefresh = true, validate = validate) { "invalid" }
        assertTrue(result.refreshFailed)
        assertEquals("valid", result.body)
    }

    @Test fun `cached body is validated too`() = runBlocking {
        ForecastCache(temporary.root, clock).get("weather") { "old-format" }
        val result = ForecastCache(temporary.root, clock).get("weather", validate = { require(it == "valid") }) { "valid" }
        assertEquals("valid", result.body)
    }

    @Test fun `request keys separate endpoints locations and query settings`() = runBlocking {
        val cache = ForecastCache(temporary.root, clock)
        cache.get("weather?point=one") { "one" }
        assertEquals("two", cache.get("weather?point=two") { "two" }.body)
        assertEquals("sea", cache.get("marine?point=one") { "sea" }.body)
        assertEquals("one", cache.get("weather?point=one") { error("No network expected") }.body)
    }

    @Test fun `concurrent identical requests coalesce`() = runBlocking {
        val cache = ForecastCache(temporary.root, clock)
        var calls = 0
        (1..5).map { async { cache.get("weather") { calls++; delay(10); "saved" } } }.awaitAll()
        assertEquals(1, calls)
    }

    @Test fun `batch refresh persists independent entries for later single-key reads`() = runBlocking {
        val cache = ForecastCache(temporary.root, clock)
        var calls = 0
        val result = cache.getMany(listOf("one", "two"), validate = { require(it.startsWith("valid")) }) { keys ->
            calls++
            keys.associateWith { "valid-$it" }
        }
        assertEquals("valid-one", result.getValue("one")!!.body)
        assertEquals("valid-two", result.getValue("two")!!.body)
        assertEquals(1, calls)

        val recreated = ForecastCache(temporary.root, clock)
        assertEquals("valid-one", recreated.get("one", validate = { require(it.startsWith("valid")) }) {
            error("No network expected")
        }.body)
    }

    @Test fun `partial batch response refreshes valid members and marks per-key stale fallback`() = runBlocking {
        val cache = ForecastCache(temporary.root, clock)
        cache.get("one") { "old-one" }
        cache.get("two") { "old-two" }
        clock.advance(Duration.ofHours(2))
        val result = cache.getMany(listOf("one", "two"), forceRefresh = true) {
            mapOf("one" to "new-one")
        }
        assertEquals("new-one", result.getValue("one")!!.body)
        assertFalse(result.getValue("one")!!.refreshFailed)
        assertEquals("old-two", result.getValue("two")!!.body)
        assertTrue(result.getValue("two")!!.refreshFailed)
    }

    @Test fun `batch cancellation is never converted into stale results`() = runBlocking {
        val cache = ForecastCache(temporary.root, clock)
        cache.get("one") { "saved" }
        try {
            cache.getMany(listOf("one"), forceRefresh = true) { throw CancellationException("cancel") }
            fail("Expected cancellation")
        } catch (_: CancellationException) { /* expected */ }
    }

    @Test fun `clock moving backwards cannot make entry fresh indefinitely`() = runBlocking {
        val cache = ForecastCache(temporary.root, clock)
        cache.get("weather") { "old" }
        clock.advance(Duration.ofMinutes(-1))
        assertEquals("new", cache.get("weather") { "new" }.body)
    }

    @Test fun `failed persistence leaves usable memory data`() = runBlocking {
        val blockedDirectory = File(temporary.root, "file-not-directory").apply { writeText("occupied") }
        val cache = ForecastCache(blockedDirectory, clock)
        assertTrue(cache.get("weather") { "fresh" }.persistenceFailed)
        assertEquals("fresh", cache.get("weather") { error("No network expected") }.body)
    }

    @Test fun `cancellation is never converted into a stale success`() = runBlocking {
        val cache = ForecastCache(temporary.root, clock)
        cache.get("weather") { "saved" }
        try {
            cache.get("weather", forceRefresh = true) { throw CancellationException("cancel") }
            fail("Expected cancellation")
        } catch (_: CancellationException) { /* expected */ }
    }
}

internal class MutableClock : Clock() {
    private var current = Instant.parse("2026-09-02T09:00:00Z")
    override fun instant(): Instant = current
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
    fun advance(duration: Duration) { current = current.plus(duration) }
}
