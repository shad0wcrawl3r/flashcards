package dev.shadowcrawler.flashcards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.shadowcrawler.flashcards.ui.theme.Blue600
import dev.shadowcrawler.flashcards.ui.theme.Gray100
import dev.shadowcrawler.flashcards.ui.theme.Green500
import dev.shadowcrawler.flashcards.ui.theme.Navy800
import dev.shadowcrawler.flashcards.ui.theme.Navy900
import dev.shadowcrawler.flashcards.ui.theme.Red500
import dev.shadowcrawler.flashcards.ui.theme.Slate400

@Composable
fun ModelDownloadScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ModelDownloadViewModel = viewModel(
        factory = ModelDownloadViewModelFactory(context)
    )
    val searchState by viewModel.searchState.collectAsState()
    val filesState by viewModel.filesState.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val hfToken by settingsViewModel.huggingFaceToken.collectAsState()

    var query by remember { mutableStateOf("gemma") }

    Box(modifier = Modifier.fillMaxSize()) {
        BackButton(
            text = "← Back",
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 64.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Find Models",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Gray100
            )
            Text(
                text = "Searches the litert-community org on Hugging Face — models there are " +
                    "already converted to a format LiteRT-LM can load, so anything found here works.",
                color = Slate400,
                fontSize = 12.sp
            )

            OutlinedTextField(
                value = hfToken,
                onValueChange = { settingsViewModel.setHuggingFaceToken(it) },
                label = { Text("Hugging Face token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Only needed for gated models (e.g. Gemma): log into huggingface.co, " +
                    "accept the model's license once, then paste a read-scoped access token here.",
                color = Slate400,
                fontSize = 12.sp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search (e.g. gemma, qwen)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { viewModel.search(query) }, enabled = query.isNotBlank()) {
                    Text("Search")
                }
            }

            when (val current = searchState) {
                SearchState.Idle -> Unit
                SearchState.Loading -> Text("Searching…", color = Slate400)
                is SearchState.Error -> Text(current.message, color = Red500)
                is SearchState.Success -> {
                    if (current.results.isEmpty()) {
                        Text("No models found.", color = Slate400)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            current.results.forEach { model ->
                                RepoRow(
                                    model = model,
                                    isOpen = (filesState as? FilesState.Success)?.repoId == model.id,
                                    onClick = { viewModel.openRepo(model.id) }
                                )
                            }
                        }
                    }
                }
            }

            when (val current = filesState) {
                FilesState.Idle -> Unit
                FilesState.Loading -> Text("Loading files…", color = Slate400)
                is FilesState.Error -> Text(current.message, color = Red500)
                is FilesState.Success -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(current.repoId, color = Gray100, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (current.files.isEmpty()) {
                            Text("No .litertlm files in this repo.", color = Slate400)
                        } else {
                            current.files.forEach { file ->
                                val inProgress = downloadState as? DownloadUiState.InProgress
                                FileRow(
                                    file = file,
                                    isDownloading = inProgress?.filename == file.path,
                                    onDownload = { viewModel.download(current.repoId, file.path, hfToken) }
                                )
                            }
                        }
                    }
                }
            }

            when (val current = downloadState) {
                DownloadUiState.Idle -> Unit
                is DownloadUiState.InProgress -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val progressFraction = if (current.totalBytes > 0) {
                            (current.bytesDownloaded.toFloat() / current.totalBytes).coerceIn(0f, 1f)
                        } else {
                            null
                        }
                        Text(
                            text = "Downloading ${current.filename}: ${mb(current.bytesDownloaded)} MB" +
                                if (current.totalBytes > 0) " / ${mb(current.totalBytes)} MB" else "",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                        if (progressFraction != null) {
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        TextButton(onClick = { viewModel.cancelDownload() }) {
                            Text("Cancel", color = Red500)
                        }
                    }
                }
                is DownloadUiState.Success -> Text("${current.filename} downloaded. Refresh models on the benchmark screen.", color = Green500)
                is DownloadUiState.Error -> Text(current.message, color = Red500)
            }
        }
    }
}

private fun mb(bytes: Long): Long = bytes / (1024 * 1024)

@Composable
private fun RepoRow(
    model: HfModelSummary,
    isOpen: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isOpen) Blue600 else Navy800
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(model.id, color = Gray100, fontSize = 14.sp)
            Text("♥ ${model.likes}", color = Slate400, fontSize = 12.sp)
        }
    }
}

@Composable
private fun FileRow(
    file: HfRepoFile,
    isDownloading: Boolean,
    onDownload: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Navy900
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(file.path, color = Gray100, fontSize = 13.sp)
                Text("${mb(file.size)} MB", color = Slate400, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onDownload, enabled = !isDownloading) {
                Text(if (isDownloading) "…" else "Get")
            }
        }
    }
}
