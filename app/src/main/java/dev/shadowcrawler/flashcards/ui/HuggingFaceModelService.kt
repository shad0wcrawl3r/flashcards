package dev.shadowcrawler.flashcards.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class HfModelSummary(
    val id: String,
    val likes: Int = 0,
    val downloads: Int = 0
)

@Serializable
data class HfRepoFile(
    val type: String,
    val path: String,
    val size: Long = 0L
)

/**
 * Talks to the public (unauthenticated) Hugging Face API, scoped to the `litert-community`
 * org — the curated source of `.task`/`.litertlm` files already converted for MediaPipe's LLM
 * Inference API, so search results are guaranteed to be the right format rather than arbitrary
 * Hugging Face models. Listing/searching needs no auth even for gated repos (e.g. Gemma); only
 * the actual file download in [ModelDownloader] does.
 */
class HuggingFaceModelService {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchModels(query: String): List<HfModelSummary> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://huggingface.co/api/models?search=$encodedQuery&author=$CURATED_ORG&limit=25"
        json.decodeFromString(get(url))
    }

    suspend fun listModelFiles(repoId: String): List<HfRepoFile> = withContext(Dispatchers.IO) {
        val url = "https://huggingface.co/api/models/$repoId/tree/main"
        json.decodeFromString<List<HfRepoFile>>(get(url))
            .filter { it.type == "file" && (it.path.endsWith(".task") || it.path.endsWith(".litertlm")) }
            .sortedBy { it.size }
    }

    fun downloadUrl(repoId: String, filename: String): String =
        "https://huggingface.co/$repoId/resolve/main/$filename"

    private fun get(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.setRequestProperty("Accept", "application/json")
            val responseCode = connection.responseCode
            check(responseCode in 200..299) { "Hugging Face request failed (HTTP $responseCode)." }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CURATED_ORG = "litert-community"
    }
}
