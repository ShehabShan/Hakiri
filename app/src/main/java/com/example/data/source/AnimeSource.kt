package com.example.data.source

import com.example.domain.model.Anime
import com.example.domain.model.Episode
import com.example.domain.model.SourceInfo
import com.example.domain.model.SourceResult
import com.example.domain.model.StreamLink
import java.util.concurrent.ConcurrentHashMap

interface AnimeSource {
    val info: SourceInfo
    suspend fun browseCatalog(page: Int): SourceResult<List<Anime>>
    suspend fun search(query: String, page: Int): SourceResult<List<Anime>>
    suspend fun getDetails(mediaId: String): SourceResult<Anime>
    suspend fun getEpisodes(mediaId: String): SourceResult<List<Episode>>
    suspend fun getStreams(mediaId: String, episodeNumber: Int): SourceResult<List<StreamLink>>
}

class SourceHealth(
    private val maxConsecutiveFailures: Int = 3
) {
    private val failureCounts = ConcurrentHashMap<String, Int>()

    fun recordSuccess(sourceId: String) {
        failureCounts[sourceId] = 0
    }

    fun recordFailure(sourceId: String) {
        failureCounts.compute(sourceId) { _, count -> (count ?: 0) + 1 }
    }

    fun getConsecutiveFailures(sourceId: String): Int {
        return failureCounts[sourceId] ?: 0
    }

    fun isDeprioritized(sourceId: String): Boolean {
        return getConsecutiveFailures(sourceId) >= maxConsecutiveFailures
    }

    fun reset(sourceId: String) {
        failureCounts.remove(sourceId)
    }

    fun resetAll() {
        failureCounts.clear()
    }
}

class SourceRegistry(
    sources: List<AnimeSource>,
    val health: SourceHealth = SourceHealth()
) {
    private val sourcesById: Map<String, AnimeSource> = sources.associateBy { it.info.id }

    fun getAllRegisteredSources(): List<SourceInfo> {
        return sourcesById.values.map { it.info }
    }

    fun getSourceById(sourceId: String): AnimeSource? {
        return sourcesById[sourceId]
    }

    fun getEnabledSources(enabledIds: Set<String>): List<AnimeSource> {
        // Order healthy sources first, deprioritized (3+ consecutive failures) last
        return sourcesById.values
            .filter { it.info.id in enabledIds }
            .sortedBy { if (health.isDeprioritized(it.info.id)) 1 else 0 }
    }
}
