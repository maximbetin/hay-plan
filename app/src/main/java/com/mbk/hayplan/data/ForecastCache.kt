package com.mbk.hayplan.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

data class CachedForecast(
    val body: String,
    val fetchedAt: Instant,
    val refreshFailed: Boolean = false,
    val persistenceFailed: Boolean = false,
)

/** One-hour cache keyed by the complete request URL. No Android dependency. */
class ForecastCache(private val directory: File, private val clock: Clock = Clock.systemUTC()) {
    private val entries = ConcurrentHashMap<String, CachedForecast>()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val batchLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun get(
        key: String,
        forceRefresh: Boolean = false,
        validate: (String) -> Unit = {},
        download: suspend () -> String,
    ): CachedForecast = locks.computeIfAbsent(key) { Mutex() }.withLock {
        val candidate = entries[key] ?: withContext(Dispatchers.IO) { read(key) }
        val existing = candidate?.takeIf { runCatching { validate(it.body) }.isSuccess }
        if (existing != null) entries[key] = existing else entries.remove(key)
        if (!forceRefresh && existing != null && isFresh(existing.fetchedAt, clock.instant())) {
            return@withLock existing.copy(refreshFailed = false)
        }
        try {
            // The client validates the response before it can replace a good cache entry.
            val response = CachedForecast(download().also(validate), clock.instant())
            val persisted = withContext(Dispatchers.IO) { write(key, response) }
            val result = response.copy(persistenceFailed = !persisted)
            entries[key] = result
            result
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            existing?.takeIf { isUsableFallback(it.fetchedAt, clock.instant()) }
                ?.copy(refreshFailed = true) ?: throw error
        }
    }

    private fun file(key: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(directory, "$hash.json")
    }

    private fun read(key: String): CachedForecast? = try {
        val json = JSONObject(file(key).readText())
        if (json.getInt("version") != VERSION) null else CachedForecast(
            json.getString("body"), Instant.ofEpochMilli(json.getLong("fetchedAt")),
        )
    } catch (_: Exception) {
        null // Missing, corrupt or inaccessible files cause a normal network load.
    }

    private fun write(key: String, entry: CachedForecast): Boolean {
        var temporary: File? = null
        return try {
            check(directory.isDirectory || directory.mkdirs())
            temporary = File.createTempFile("forecast-", ".tmp", directory)
            temporary.writeText(JSONObject().put("version", VERSION)
                .put("fetchedAt", entry.fetchedAt.toEpochMilli()).put("body", entry.body).toString())
            try {
                Files.move(temporary.toPath(), file(key).toPath(),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file(key).toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (_: Exception) {
            false // Fresh data is still usable in memory if persistence fails.
        } finally {
            temporary?.delete()
        }
    }

    /**
     * Refresh several independent cache keys with one download while retaining per-key
     * validation, persistence, freshness and stale fallback semantics.
     */
    suspend fun getMany(
        keys: List<String>,
        forceRefresh: Boolean = false,
        validate: (String) -> Unit = {},
        download: suspend (List<String>) -> Map<String, String>,
    ): Map<String, CachedForecast?> {
        val distinctKeys = keys.distinct()
        val lockKey = distinctKeys.sorted().joinToString("\u0000")
        return batchLocks.computeIfAbsent(lockKey) { Mutex() }.withLock {
            val existing = distinctKeys.associateWith { key ->
                val candidate = entries[key] ?: withContext(Dispatchers.IO) { read(key) }
                candidate?.takeIf { runCatching { validate(it.body) }.isSuccess }?.also { entries[key] = it }
                    ?: run { entries.remove(key); null }
            }
            val now = clock.instant()
            val reusable = if (forceRefresh) emptyMap() else existing.filterValues {
                it != null && isFresh(it.fetchedAt, now)
            }.mapValues { requireNotNull(it.value).copy(refreshFailed = false) }
            val requested = distinctKeys.filter { it !in reusable }
            if (requested.isEmpty()) return@withLock reusable

            val downloaded = try {
                download(requested)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyMap()
            }
            val fetchedAt = clock.instant()
            val refreshed = requested.associateWith { key ->
                val body = downloaded[key]?.takeIf { runCatching { validate(it) }.isSuccess }
                if (body != null) {
                    val response = CachedForecast(body, fetchedAt)
                    val persisted = withContext(Dispatchers.IO) { write(key, response) }
                    response.copy(persistenceFailed = !persisted).also { entries[key] = it }
                } else {
                    existing[key]?.takeIf { isUsableFallback(it.fetchedAt, fetchedAt) }
                        ?.copy(refreshFailed = true)
                }
            }
            reusable + refreshed
        }
    }

    companion object {
        private const val VERSION = 1
        val TTL: Duration = Duration.ofHours(1)
        val MAX_STALE_AGE: Duration = Duration.ofHours(12)

        fun isFresh(fetchedAt: Instant, now: Instant): Boolean {
            val age = Duration.between(fetchedAt, now)
            return !age.isNegative && age < TTL
        }

        fun isUsableFallback(fetchedAt: Instant, now: Instant): Boolean {
            val age = Duration.between(fetchedAt, now)
            return !age.isNegative && age <= MAX_STALE_AGE
        }
    }
}
