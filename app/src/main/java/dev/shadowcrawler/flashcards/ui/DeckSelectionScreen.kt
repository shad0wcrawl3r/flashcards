package dev.shadowcrawler.flashcards.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.shadowcrawler.flashcards.model.FlashcardDatabase
import dev.shadowcrawler.flashcards.ui.theme.Blue600
import dev.shadowcrawler.flashcards.ui.theme.Gray100
import dev.shadowcrawler.flashcards.ui.theme.Green500
import dev.shadowcrawler.flashcards.ui.theme.Navy800
import dev.shadowcrawler.flashcards.ui.theme.Navy900
import dev.shadowcrawler.flashcards.ui.theme.Red500
import dev.shadowcrawler.flashcards.ui.theme.Slate400

@Composable
fun DeckSelectionScreen(
    database: FlashcardDatabase,
    settingsViewModel: SettingsViewModel,
    onDeckSelected: (Long) -> Unit,
    onImportDeck: () -> Unit,
    onCreateDeck: () -> Unit,
    onEditDeck: (Long) -> Unit,
    onOpenLlmBenchmark: () -> Unit,
    onOpenLayaBenchmark: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: DeckListViewModel = viewModel(
        factory = DeckListViewModelFactory(database)
    )
    val shareViewModel: ShareViewModel = viewModel(
        factory = ShareViewModelFactory(database)
    )
    val decks by viewModel.decks.collectAsState()
    val selectedDeckIds by viewModel.selectedDeckIds.collectAsState()
    val shareState by shareViewModel.state.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAddChooser by remember { mutableStateOf(false) }
    var showShareChooser by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }

    val recognitionAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val onDeviceRecognitionAvailable = remember { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }
    val ttsEnabled by settingsViewModel.ttsEnabled.collectAsState()
    val onDeviceRecognitionEnabled by settingsViewModel.onDeviceRecognitionEnabled.collectAsState()

    val isSelectionMode = selectedDeckIds.isNotEmpty()

    BackHandler(enabled = isSelectionMode) { viewModel.clearSelection() }
    BackHandler(enabled = showSettingsMenu) { showSettingsMenu = false }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadDecks()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(shareViewModel) {
        shareViewModel.events.collect { event ->
            when (event) {
                is ShareEvent.OpenShareSheet -> {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, event.url)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share deck link"))
                    shareViewModel.dismiss()
                    viewModel.clearSelection()
                }
            }
        }
    }

    val filteredDecks = remember(decks, searchQuery) {
        if (searchQuery.isBlank()) {
            decks
        } else {
            decks.filter { summary ->
                summary.deck.name.contains(searchQuery, ignoreCase = true) ||
                    summary.deck.tags.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isSelectionMode) {
                SelectionHeader(
                    selectedCount = selectedDeckIds.size,
                    onClose = { viewModel.clearSelection() }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeckSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    HamburgerButton(onClick = { showSettingsMenu = true })
                }
            }

            if (filteredDecks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (decks.isEmpty()) {
                            "Loading decks..."
                        } else {
                            "No decks match \"$searchQuery\"."
                        },
                        color = Gray100
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        top = 8.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredDecks, key = { it.deck.id }) { summary ->
                        DeckCard(
                            summary = summary,
                            isSelected = summary.deck.id in selectedDeckIds,
                            selectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(summary.deck.id)
                                } else {
                                    onDeckSelected(summary.deck.id)
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(summary.deck.id) }
                        )
                    }
                }
            }
        }

        DeckActionBar(
            selectionMode = isSelectionMode,
            singleDeckSelected = selectedDeckIds.size == 1,
            onAdd = { showAddChooser = true },
            onEdit = { selectedDeckIds.firstOrNull()?.let { onEditDeck(it) } },
            onShare = { showShareChooser = true },
            onDelete = { showDeleteConfirm = true }
        )

        if (shareState is ShareUiState.Loading) {
            ShareLoadingOverlay()
        }

        SettingsMenuOverlay(
            visible = showSettingsMenu,
            onDismiss = { showSettingsMenu = false },
            recognitionAvailable = recognitionAvailable,
            onDeviceRecognitionAvailable = onDeviceRecognitionAvailable,
            ttsEnabled = ttsEnabled,
            onTtsEnabledChange = settingsViewModel::setTtsEnabled,
            onDeviceRecognitionEnabled = onDeviceRecognitionEnabled,
            onOnDeviceRecognitionEnabledChange = settingsViewModel::setOnDeviceRecognitionEnabled,
            onOpenLlmBenchmark = {
                showSettingsMenu = false
                onOpenLlmBenchmark()
            },
            onOpenLayaBenchmark = {
                showSettingsMenu = false
                onOpenLayaBenchmark()
            }
        )
    }

    if (showAddChooser) {
        AddDeckChooserDialog(
            onDismiss = { showAddChooser = false },
            onChooseCreate = {
                showAddChooser = false
                onCreateDeck()
            },
            onChooseImport = {
                showAddChooser = false
                onImportDeck()
            }
        )
    }

    if (showShareChooser) {
        ShareChooserDialog(
            deckCount = selectedDeckIds.size,
            onDismiss = { showShareChooser = false },
            onChoosePastebin = {
                showShareChooser = false
                shareViewModel.shareAsLink(selectedDeckIds.toList())
            },
            onChooseQrCode = {
                showShareChooser = false
                shareViewModel.shareAsQrCode(selectedDeckIds.toList())
            }
        )
    }

    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            deckCount = selectedDeckIds.size,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                viewModel.deleteSelected()
            }
        )
    }

    when (val current = shareState) {
        is ShareUiState.QrReady -> {
            QrShareDialog(
                url = current.url,
                bitmap = current.qrBitmap,
                onShareLink = { shareViewModel.requestShareSheet(current.url) },
                onDismiss = {
                    shareViewModel.dismiss()
                    viewModel.clearSelection()
                }
            )
        }
        is ShareUiState.Error -> {
            AlertDialog(
                onDismissRequest = { shareViewModel.dismiss() },
                title = { Text("Share Failed") },
                text = { Text(current.message) },
                confirmButton = {
                    TextButton(onClick = { shareViewModel.dismiss() }) { Text("OK") }
                }
            )
        }
        else -> Unit
    }
}

@Composable
private fun SelectionHeader(
    selectedCount: Int,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$selectedCount selected",
            color = Gray100,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        TextButton(onClick = onClose) {
            Text("✕ Cancel", color = Slate400)
        }
    }
}

@Composable
private fun DeckSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Navy800
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔍", fontSize = 16.sp, color = Slate400)
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Gray100, fontSize = 16.sp),
                    cursorBrush = SolidColor(Gray100)
                )
                if (query.isEmpty()) {
                    Text("Search…", color = Slate400, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun HamburgerButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Navy800,
        contentColor = Gray100
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("☰", fontSize = 20.sp)
        }
    }
}

/** Right-edge slide-out panel for speech settings, dismissed by tapping the scrim or back. */
@Composable
private fun BoxScope.SettingsMenuOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    recognitionAvailable: Boolean,
    onDeviceRecognitionAvailable: Boolean,
    ttsEnabled: Boolean,
    onTtsEnabledChange: (Boolean) -> Unit,
    onDeviceRecognitionEnabled: Boolean,
    onOnDeviceRecognitionEnabledChange: (Boolean) -> Unit,
    onOpenLlmBenchmark: () -> Unit,
    onOpenLayaBenchmark: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(animationSpec = tween(250)) { fullWidth -> fullWidth },
        exit = slideOutHorizontally(animationSpec = tween(250)) { fullWidth -> fullWidth },
        modifier = Modifier.align(Alignment.CenterEnd)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(300.dp),
            color = Navy800
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("Settings", color = Gray100, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(24.dp))

                Text("Speech support", color = Slate400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Speech recognition: ${if (recognitionAvailable) "available" else "unavailable"}",
                    color = Gray100,
                    fontSize = 13.sp
                )
                Text(
                    text = "On-device recognition: ${if (onDeviceRecognitionAvailable) "available" else "unavailable"}",
                    color = Gray100,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                SettingsToggleRow(
                    label = "Text-to-speech",
                    description = "Read cards aloud while practicing.",
                    checked = ttsEnabled,
                    onCheckedChange = onTtsEnabledChange
                )

                if (onDeviceRecognitionAvailable) {
                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsToggleRow(
                        label = "On-device speech recognition",
                        description = "Answer cards by speaking. Coming soon.",
                        checked = onDeviceRecognitionEnabled,
                        onCheckedChange = onOnDeviceRecognitionEnabledChange
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    SpeechRecognitionSandbox()
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("Answer matching (experimental)", color = Slate400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    onClick = onOpenLlmBenchmark,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Navy900
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Open LLM benchmark →", color = Gray100, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Load a local on-device model and test how well it judges answers.",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    onClick = onOpenLayaBenchmark,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Navy900
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Open Laya judge →", color = Gray100, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Typed-decision model (not a freeform LLM) — 82.6% on the Python eval set.",
                            color = Slate400,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, color = Gray100, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, color = Slate400, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Gray100,
                checkedTrackColor = Blue600
            )
        )
    }
}

/** Standalone test rig for on-device recognition: tap to listen, see what it heard. */
@Composable
private fun SpeechRecognitionSandbox() {
    val context = LocalContext.current
    var partialText by remember { mutableStateOf("") }
    var finalText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val recognitionController = remember {
        SpeechRecognitionController(
            context = context.applicationContext,
            onPartialResult = { partialText = it },
            onFinalResult = { text ->
                finalText = text
                partialText = ""
            },
            onListeningChanged = { isListening = it },
            onErrorMessage = { message ->
                errorMessage = message
                statusMessage = null
                isListening = false
            },
            onStatusMessage = { message ->
                statusMessage = message
                errorMessage = null
            }
        )
    }
    DisposableEffect(recognitionController) {
        onDispose { recognitionController.destroy() }
    }

    fun beginListening() {
        errorMessage = null
        statusMessage = null
        finalText = ""
        partialText = ""
        recognitionController.startListening()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginListening()
        } else {
            errorMessage = "Microphone permission is required to test recognition."
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Navy900
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text("Recognition sandbox", color = Gray100, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap the mic, say something, and the recognized text shows up below.",
                color = Slate400,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Navy800
            ) {
                Text(
                    text = partialText.ifBlank { finalText }.ifBlank { "…" },
                    modifier = Modifier.padding(12.dp),
                    color = Gray100,
                    fontSize = 14.sp
                )
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMessage.orEmpty(), color = Red500, fontSize = 12.sp)
            }

            if (statusMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(statusMessage.orEmpty(), color = Green500, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                onClick = {
                    if (isListening) {
                        recognitionController.stopListening()
                    } else {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            beginListening()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = if (isListening) Red500 else Blue600
            ) {
                Text(
                    text = if (isListening) "⏹ Stop listening" else "🎤 Start listening",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    color = Gray100,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckCard(
    summary: DeckSummary,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Blue600 else Navy800),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = summary.deck.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gray100,
                    modifier = Modifier.padding(end = if (selectionMode) 32.dp else 0.dp)
                )

                if (!summary.deck.description.isNullOrBlank()) {
                    Text(
                        text = summary.deck.description,
                        fontSize = 14.sp,
                        color = Slate400,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Text(
                    text = "${summary.cardCount} card${if (summary.cardCount == 1) "" else "s"}",
                    fontSize = 13.sp,
                    color = Slate400,
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (summary.deck.tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        summary.deck.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Blue600
                            ) {
                                Text(
                                    text = "#$tag",
                                    fontSize = 12.sp,
                                    color = Gray100,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (selectionMode) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) Green500 else Navy900,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(24.dp)
                ) {
                    if (isSelected) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✓", color = Gray100, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Anchors the add button bottom-end; entering selection mode slides it to bottom-center
 * and then morphs it into a Share/Delete button pair.
 */
@Composable
private fun BoxScope.DeckActionBar(
    selectionMode: Boolean,
    singleDeckSelected: Boolean,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val horizontalBias = remember { Animatable(1f) }
    var showActions by remember { mutableStateOf(false) }

    LaunchedEffect(selectionMode) {
        if (selectionMode) {
            horizontalBias.animateTo(0f, animationSpec = tween(durationMillis = 350))
            showActions = true
        } else {
            showActions = false
            horizontalBias.animateTo(1f, animationSpec = tween(durationMillis = 350))
        }
    }

    Box(
        modifier = Modifier
            .align(BiasAlignment(horizontalBias = horizontalBias.value, verticalBias = 1f))
            .padding(24.dp)
    ) {
        AnimatedContent(
            targetState = showActions,
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.7f, animationSpec = tween(200))) togetherWith
                    (fadeOut(tween(150)) + scaleOut(targetScale = 0.7f, animationSpec = tween(150)))
            },
            label = "deckActionBar"
        ) { actionsVisible ->
            if (actionsVisible) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (singleDeckSelected) {
                        RoundActionButton(onClick = onEdit, containerColor = Blue600, label = "✏")
                    }
                    RoundActionButton(onClick = onShare, containerColor = Blue600, label = "↗")
                    RoundActionButton(onClick = onDelete, containerColor = Red500, label = "🗑")
                }
            } else {
                RoundActionButton(onClick = onAdd, containerColor = Blue600, label = "+")
            }
        }
    }
}

@Composable
private fun RoundActionButton(
    onClick: () -> Unit,
    containerColor: Color,
    label: String
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = containerColor,
        contentColor = Gray100
    ) {
        Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddDeckChooserDialog(
    onDismiss: () -> Unit,
    onChooseCreate: () -> Unit,
    onChooseImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a Deck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ShareOptionRow(
                    label = "Create New Deck",
                    description = "Build a deck by hand, optimized for writing multiple-choice questions.",
                    onClick = onChooseCreate
                )

                ShareOptionRow(
                    label = "Import Deck",
                    description = "Bring in a deck from a link, QR code, or file.",
                    onClick = onChooseImport
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ShareChooserDialog(
    deckCount: Int,
    onDismiss: () -> Unit,
    onChoosePastebin: () -> Unit,
    onChooseQrCode: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share $deckCount deck${if (deckCount == 1) "" else "s"}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose how the other person should receive it.", color = Slate400)

                ShareOptionRow(
                    label = "Pastebin Link",
                    description = "Uploads the deck data to paste.rs, a public paste service, and shares the link.",
                    onClick = onChoosePastebin
                )

                ShareOptionRow(
                    label = "QR Code",
                    description = "⚠ Also uploads the deck data to paste.rs first, then shows a QR code of that link to scan.",
                    onClick = onChooseQrCode
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ShareOptionRow(
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Navy900
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = Gray100, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, color = Slate400, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    deckCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete $deckCount deck${if (deckCount == 1) "" else "s"}?") },
        text = { Text("This can't be undone.", color = Slate400) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = Red500) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun QrShareDialog(
    url: String,
    bitmap: android.graphics.Bitmap,
    onShareLink: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan to Import") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR code linking to the shared decks",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(url, fontSize = 12.sp, color = Slate400, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            TextButton(onClick = onShareLink) { Text("Share Link") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun ShareLoadingOverlay() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Navy800
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(color = Blue600, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Preparing share link…", color = Gray100)
            }
        }
    }
}
