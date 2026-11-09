package com.example.data.jikan

import com.example.domain.model.AppError
import com.example.domain.model.SourceResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException

interface JikanApi {
    @GET("top/anime")
    suspend fun getTopAnime(
        @Query("limit") limit: Int = 20,
        @Query("sfw") sfw: Boolean = true
    ): JikanListResponse

    @GET("seasons/now")
    suspend fun getSeasonNow(
        @Query("limit") limit: Int = 20,
        @Query("sfw") sfw: Boolean = true
    ): JikanListResponse

    @GET("anime")
    suspend fun searchAnime(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("sfw") sfw: Boolean = true
    ): JikanListResponse

    @GET("anime/{id}")
    suspend fun getAnimeById(
        @Path("id") malId: Long
    ): JikanSingleResponse

    companion object {
        const val BASE_URL = "https://api.jikan.moe/v4/"
    }
}

class JikanRateLimiter(
    private val minIntervalMs: Long = 400L,
    private val maxRequestsPerMinute: Int = 55,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val mutex = Mutex()
    private var lastRequestAtMs: Long = -minIntervalMs
    private val recentRequestTimestamps = ArrayDeque<Long>()
    private val retryBackoffDelaysMs = listOf(1_000L, 2_000L, 4_000L)

    suspend fun <T> execute(block: suspend () -> T): SourceResult<T> {
        var attempt = 0
        while (true) {
            acquireSlot()
            try {
                val response = block()
                return SourceResult.Success(response)
            } catch (e: HttpException) {
                if (e.code() == 429) {
                    if (attempt < retryBackoffDelaysMs.size) {
                        val backoffMs = retryBackoffDelaysMs[attempt]
                        attempt++
                        delay(backoffMs)
                        continue
                    } else {
                        return SourceResult.Failure(AppError.RateLimited)
                    }
                } else if (e.code() == 404) {
                    return SourceResult.Failure(AppError.NotFound)
                } else {
                    return SourceResult.Failure(AppError.SourceUnavailable("jikan"))
                }
            } catch (e: IOException) {
                return SourceResult.Failure(AppError.Network)
            } catch (e: kotlinx.serialization.SerializationException) {
                return SourceResult.Failure(
                    AppError.ParseFailure("jikan", e.message ?: "Malformed Jikan JSON")
                )
            } catch (e: Exception) {
                return SourceResult.Failure(AppError.Unknown(e.message))
            }
        }
    }

    private suspend fun acquireSlot() {
        mutex.withLock {
            val now = clockMs()
            // Evict timestamps older than 60 seconds
            while (recentRequestTimestamps.isNotEmpty() && now - recentRequestTimestamps.first() >= 60_000L) {
                recentRequestTimestamps.removeFirst()
            }

            if (recentRequestTimestamps.size >= maxRequestsPerMinute) {
                val oldest = recentRequestTimestamps.first()
                val waitWindowMs = (60_000L - (now - oldest)).coerceAtLeast(minIntervalMs)
                delay(waitWindowMs)
            }

            val afterWindow = clockMs()
            val elapsedSinceLast = afterWindow - lastRequestAtMs
            if (elapsedSinceLast < minIntervalMs) {
                delay(minIntervalMs - elapsedSinceLast)
            }

            val finalTimestamp = clockMs()
            lastRequestAtMs = finalTimestamp
            recentRequestTimestamps.addLast(finalTimestamp)
        }
    }
}
