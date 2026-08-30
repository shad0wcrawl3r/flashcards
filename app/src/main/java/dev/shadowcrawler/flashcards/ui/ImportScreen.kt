package dev.shadowcrawler.flashcards.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.shadowcrawler.flashcards.model.FlashcardDatabase
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import dev.shadowcrawler.flashcards.ui.theme.Gray100
import dev.shadowcrawler.flashcards.ui.theme.Green500
import dev.shadowcrawler.flashcards.ui.theme.Red500
import dev.shadowcrawler.flashcards.ui.theme.Slate400

@Composable
fun ImportScreen(
    database: FlashcardDatabase,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ImportViewModel = viewModel(factory = ImportViewModelFactory(database))
    val state by viewModel.state.collectAsState()
    var urlText by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val text = context.contentResolver.openInputStream(it)
                ?.bufferedReader()
                ?.use { reader -> reader.readText() }
            if (text != null) {
                viewModel.importFromJsonText(text)
            }
        }
    }

    val qrLauncher = rememberLauncherForActivityResult(ScanQRCode()) { result ->
        when (result) {
            is QRResult.QRSuccess -> {
                val scannedUrl = result.content.rawValue
                if (scannedUrl != null) {
                    urlText = scannedUrl
                    viewModel.importFromUrl(scannedUrl)
                } else {
                    viewModel.reportError("Scanned QR code had no readable content.")
                }
            }
            is QRResult.QRUserCanceled -> Unit
            is QRResult.QRMissingPermission -> viewModel.reportError("Camera permission is required to scan a QR code.")
            is QRResult.QRError -> viewModel.reportError(result.exception.message ?: "QR scan failed.")
        }
    }

    val isLoading = state is ImportUiState.Loading

    Box(modifier = Modifier.fillMaxSize()) {
        BackButton(
            text = "← Back",
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 64.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Import a Deck",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Gray100
            )

            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Deck URL") },
                singleLine = true,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { viewModel.importFromUrl(urlText) },
                enabled = !isLoading && urlText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Import from URL")
            }

            Text(
                text = "or",
                color = Slate400,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                textAlign = TextAlign.Center
            )

            Button(
                onClick = { qrLauncher.launch(null) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan QR Code")
            }

            Button(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Select File")
            }

            when (val current = state) {
                is ImportUiState.Loading -> Text("Importing…", color = Slate400)
                is ImportUiState.Error -> Text(current.message, color = Red500)
                is ImportUiState.Success -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (current.importedNames.isNotEmpty()) {
                            val names = current.importedNames.joinToString(", ") { "\"$it\"" }
                            val deckWord = if (current.importedNames.size == 1) "deck" else "decks"
                            Text(
                                "Imported $names (${current.cardCount} cards across ${current.importedNames.size} $deckWord). Go back to see it.",
                                color = Green500
                            )
                        }
                        if (current.skippedNames.isNotEmpty()) {
                            val names = current.skippedNames.joinToString(", ") { "\"$it\"" }
                            Text(
                                "Already imported, skipped: $names.",
                                color = Slate400
                            )
                        }
                    }
                }
                ImportUiState.Idle -> {}
            }
        }
    }
}
