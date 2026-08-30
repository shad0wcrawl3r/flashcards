package dev.shadowcrawler.flashcards.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed interface DownloadProgress {
    data class InProgress(val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress
    data object Complete : DownloadProgress
}

private const val BUFFER_SIZE = 256 * 1024
private const val PROGRESS_REPORT_INTERVAL_BYTES = 2L * 1024 * 1024

class ModelDownloader {

    /**
     * Streams [url] to [destination] via a `.part` sibling file, renamed on success so a
     * cancelled or failed download never leaves a corrupt file for [LlmModelStore] to pick up.
     */
    suspend fun download(
        url: String,
        destination: File,
        authToken: String?,
        onProgress: (DownloadProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        val tempFile = File(destination.parentFile, "${destination.name}.part")
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            if (!authToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
            }
            val responseCode = connection.responseCode
            check(responseCode in 200..299) {
                if (responseCode == 401 || responseCode == 403) {
                    "Access denied (HTTP $responseCode). This model may be gated — accept its " +
                        "license on huggingface.co, then set a Hugging Face token above."
                } else {
                    "Download failed (HTTP $responseCode)."
                }
            }
            val totalBytes = connection.contentLengthLong

            var bytesDownloaded = 0L
            var lastReportedBytes = 0L
            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesDownloaded += read
                        if (bytesDownloaded - lastReportedBytes >= PROGRESS_REPORT_INTERVAL_BYTES) {
                            lastReportedBytes = bytesDownloaded
                            onProgress(DownloadProgress.InProgress(bytesDownloaded, totalBytes))
                        }
                    }
                }
            }

            check(tempFile.renameTo(destination)) { "Failed to finalize downloaded file." }
            onProgress(DownloadProgress.Complete)
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }
}
