package dev.shadowcrawler.flashcards.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uploads text to paste.rs, a keyless paste service that serves each paste back
 * as raw text — the same shape [DeckImporter.importFromUrl] already expects.
 */
class PastebinService {

    suspend fun upload(text: String): String = withContext(Dispatchers.IO) {
        val connection = URL(PASTE_ENDPOINT).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            connection.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }

            val responseCode = connection.responseCode
            check(responseCode in 200..299) { "Paste upload failed (HTTP $responseCode)." }

            connection.inputStream.bufferedReader().use { it.readText() }.trim()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val PASTE_ENDPOINT = "https://paste.rs/"
    }
}
