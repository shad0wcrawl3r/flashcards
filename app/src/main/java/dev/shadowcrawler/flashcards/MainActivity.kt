package dev.shadowcrawler.flashcards

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.shadowcrawler.flashcards.model.CardDisplayState
import dev.shadowcrawler.flashcards.model.CardType
import dev.shadowcrawler.flashcards.model.Flashcard
import dev.shadowcrawler.flashcards.data.DatabaseProvider
import dev.shadowcrawler.flashcards.ui.BackButton
import dev.shadowcrawler.flashcards.ui.DeckEditorScreen
import dev.shadowcrawler.flashcards.ui.DeckSelectionScreen
import dev.shadowcrawler.flashcards.ui.ImportScreen
import dev.shadowcrawler.flashcards.ui.LayaBenchmarkScreen
import dev.shadowcrawler.flashcards.ui.LlmBenchmarkScreen
import dev.shadowcrawler.flashcards.ui.ModelDownloadScreen
import dev.shadowcrawler.flashcards.ui.FlashcardViewModel
import dev.shadowcrawler.flashcards.ui.FlashcardViewModelFactory
import dev.shadowcrawler.flashcards.ui.SettingsViewModel
import dev.shadowcrawler.flashcards.ui.SettingsViewModelFactory
import dev.shadowcrawler.flashcards.ui.HandsFreeState
import dev.shadowcrawler.flashcards.ui.rememberHandsFreeController
import dev.shadowcrawler.flashcards.ui.rememberTtsController
import dev.shadowcrawler.flashcards.ui.theme.FlashcardsTheme
import dev.shadowcrawler.flashcards.ui.theme.Blue600
import dev.shadowcrawler.flashcards.ui.theme.Navy800
import dev.shadowcrawler.flashcards.ui.theme.Navy900
import dev.shadowcrawler.flashcards.ui.theme.Gray100
import dev.shadowcrawler.flashcards.ui.theme.Green500
import dev.shadowcrawler.flashcards.ui.theme.Red500
import dev.shadowcrawler.flashcards.ui.theme.Slate400
import androidx.core.graphics.drawable.toDrawable

// Scoring doesn't work well with the current swipe/reveal flow yet — disabled for now,
// not removed. The score tracking below stays in place so it's easy to bring back.
private const val SCORING_ENABLED = false

// Gives the user a moment to actually process the spoken correct answer before the card
// changes out from under them — hands-free has no swipe to pace themselves with otherwise.
private const val HANDS_FREE_ADVANCE_DELAY_MS = 1800L

private const val ROUTE_DECK_SELECTION = "deckSelection"
private const val ROUTE_FLASHCARDS = "flashcards/{deckId}"
private const val ROUTE_IMPORT = "import"
private const val ROUTE_DECK_EDITOR = "deckEditor/{deckId}"
private const val ROUTE_LLM_BENCHMARK = "llmBenchmark"
private const val ROUTE_LAYA_BENCHMARK = "layaBenchmark"
private const val ROUTE_MODEL_DOWNLOAD = "modelDownload"
private const val ARG_DECK_ID = "deckId"
/** Sentinel passed as the deckId route arg to open the editor in create-new-deck mode. */
private const val NEW_DECK_ID = 0L


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = DatabaseProvider.get(applicationContext)

        setContent {
            FlashcardsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModelFactory(applicationContext)
                    )

                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_DECK_SELECTION,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {

                        composable(ROUTE_DECK_SELECTION) {
                            DeckSelectionScreen(
                                database = database,
                                settingsViewModel = settingsViewModel,
                                onDeckSelected = { deckId ->
                                    navController.navigate("flashcards/$deckId")
                                },
                                onImportDeck = {
                                    navController.navigate(ROUTE_IMPORT)
                                },
                                onCreateDeck = {
                                    navController.navigate("deckEditor/$NEW_DECK_ID")
                                },
                                onEditDeck = { deckId ->
                                    navController.navigate("deckEditor/$deckId")
                                },
                                onOpenLlmBenchmark = {
                                    navController.navigate(ROUTE_LLM_BENCHMARK)
                                },
                                onOpenLayaBenchmark = { navController.navigate(ROUTE_LAYA_BENCHMARK) }
                            )

                        }

                        composable(ROUTE_LLM_BENCHMARK) {
                            LlmBenchmarkScreen(
                                onBack = { navController.popBackStack() },
                                onOpenModelDownload = { navController.navigate(ROUTE_MODEL_DOWNLOAD) }
                            )
                        }

                        composable(ROUTE_LAYA_BENCHMARK) {
                            LayaBenchmarkScreen(
                                onBack = { navController.popBackStack() },
                                settingsViewModel = settingsViewModel
                            )
                        }

                        composable(ROUTE_MODEL_DOWNLOAD) {
                            ModelDownloadScreen(
                                settingsViewModel = settingsViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(ROUTE_IMPORT) {
                            ImportScreen(
                                database = database,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = ROUTE_DECK_EDITOR,
                            arguments = listOf(navArgument(ARG_DECK_ID) { type = NavType.LongType })
                        ) { backStackEntry ->
                            val deckId = backStackEntry.arguments?.getLong(ARG_DECK_ID) ?: NEW_DECK_ID
                            DeckEditorScreen(
                                database = database,
                                deckId = deckId,
                                onBack = { navController.popBackStack() },
                                onSaved = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = ROUTE_FLASHCARDS,
                            arguments = listOf(navArgument(ARG_DECK_ID) { type = NavType.LongType })
                        ) { backStackEntry ->
                            val deckId = backStackEntry.arguments?.getLong(ARG_DECK_ID) ?: 0L
                            val viewModel: FlashcardViewModel = viewModel(
                                factory = FlashcardViewModelFactory(database, deckId)
                            )
                            val flashcards by viewModel.flashcards.collectAsState()

                            FlashcardScreen(
                                flashcards = flashcards,
                                settingsViewModel = settingsViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlashcardScreen(
    flashcards: List<Flashcard>,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    // Cards are presented in a random order per pass through the deck.
    var shuffledDeck by remember(flashcards) { mutableStateOf(flashcards.shuffled()) }
    var currentIndex by remember(flashcards) { mutableIntStateOf(0) }
    var score by remember(flashcards) { mutableIntStateOf(0) }
    // Increments on every advance, even ones that land back on the same index (e.g. a
    // single-card deck) — currentIndex alone can't key per-card state resets in that case.
    var cardSequence by remember(flashcards) { mutableIntStateOf(0) }

    val ttsEnabled by settingsViewModel.ttsEnabled.collectAsState()
    val onDeviceRecognitionEnabled by settingsViewModel.onDeviceRecognitionEnabled.collectAsState()
    val handsFreeAssessmentEnabled by settingsViewModel.handsFreeAssessmentEnabled.collectAsState()
    val handsFreeActive = ttsEnabled && onDeviceRecognitionEnabled && handsFreeAssessmentEnabled

    val ttsController = rememberTtsController()
    val handsFreeController = rememberHandsFreeController()
    val handsFreeState by handsFreeController.state.collectAsState()

    val context = LocalContext.current
    var micPermissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micPermissionGranted = granted }
    // Ask once, as soon as hands-free assessment is actually usable, rather than mid-question —
    // a permission dialog stealing focus right after TTS finishes speaking would be jarring.
    LaunchedEffect(handsFreeActive) {
        if (handsFreeActive && !micPermissionGranted) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Stop mid-utterance so leftover speech from the previous card doesn't bleed into the next,
    // and drop any hands-free result/error from the card just left.
    LaunchedEffect(cardSequence) {
        ttsController.stop()
        handsFreeController.reset()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackButton(
            text = "← Decks",
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
        )

        if (shuffledDeck.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No cards in this deck yet.", color = Gray100)
            }
            return@Box
        }

        // Protect against the list becoming smaller while the screen is active.
        if (currentIndex >= shuffledDeck.size) {
            currentIndex = shuffledDeck.lastIndex
        }

        val currentCard = shuffledDeck[currentIndex]

        // Keyed by cardSequence so each new card starts unrevealed/unanswered.
        var showAnswer by remember(cardSequence) { mutableStateOf(false) }
        var selectedChoice by remember(cardSequence) { mutableStateOf<String?>(null) }

        val hasAnswered = when (currentCard.type) {
            CardType.QA -> showAnswer
            CardType.MCQ -> selectedChoice != null
        }

        fun advance() {
            if (SCORING_ENABLED) {
                score += when (currentCard.type) {
                    CardType.QA -> currentCard.difficulty
                    CardType.MCQ -> if (selectedChoice == currentCard.answer) currentCard.difficulty else 0
                }
            }
            cardSequence += 1
            if (currentIndex + 1 >= shuffledDeck.size) {
                shuffledDeck = flashcards.shuffled()
                currentIndex = 0
            } else {
                currentIndex += 1
            }
        }

        val displayState = CardDisplayState(
            card = currentCard,
            showingAnswer = showAnswer
        )

        val coroutineScope = rememberCoroutineScope()
        val density = LocalDensity.current
        // A fresh Animatable per card so it always starts centered under the finger.
        val dragOffsetX = remember(cardSequence) { Animatable(0f) }
        val swipeThresholdPx = with(density) { 96.dp.toPx() }
        val flyDistancePx = with(density) { 700.dp.toPx() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${currentIndex + 1} of ${shuffledDeck.size}", color = Gray100, fontSize = 14.sp)
                Text("   |   ", color = Slate400, fontSize = 14.sp)
                Text(
                    text = if (currentCard.type == CardType.QA) "Q/A" else "MCQ",
                    color = Gray100,
                    fontSize = 14.sp
                )
                if (SCORING_ENABLED) {
                    Text("   |   ", color = Slate400, fontSize = 14.sp)
                    Text("Score: $score", color = Gray100, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            val cardTransition = updateTransition(
                targetState = displayState,
                label = "flashcardTransition"
            )

            cardTransition.AnimatedContent(
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 300))
                }
            ) { state ->

                var cardModifier = Modifier
                    .testTag("flashcard")
                    .fillMaxWidth()
                    .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
                    .graphicsLayer { rotationZ = (dragOffsetX.value / 40f).coerceIn(-12f, 12f) }
                    .pointerInput(cardSequence) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                // Re-read live state here rather than closing over `hasAnswered`,
                                // which is captured once when this gesture-detector coroutine
                                // (re)starts on cardSequence change and would otherwise go stale
                                // the moment the card is answered without advancing.
                                val answered = when (currentCard.type) {
                                    CardType.QA -> showAnswer
                                    CardType.MCQ -> selectedChoice != null
                                }
                                coroutineScope.launch {
                                    if (answered && abs(dragOffsetX.value) > swipeThresholdPx) {
                                        val target = if (dragOffsetX.value > 0) flyDistancePx else -flyDistancePx
                                        dragOffsetX.animateTo(target, animationSpec = tween(200))
                                        advance()
                                    } else {
                                        dragOffsetX.animateTo(0f, animationSpec = spring())
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch { dragOffsetX.animateTo(0f, animationSpec = spring()) }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch { dragOffsetX.snapTo(dragOffsetX.value + dragAmount) }
                            }
                        )
                    }

                if (state.card.type == CardType.QA) {
                    cardModifier = cardModifier.pointerInput(cardSequence) {
                        detectTapGestures(onTap = { showAnswer = !showAnswer })
                    }
                }

                Card(
                    modifier = cardModifier,
                    colors = CardDefaults.cardColors(
                        containerColor = when (state.card.type) {
                            CardType.QA -> if (state.showingAnswer) Blue600 else Navy800
                            CardType.MCQ -> Navy800
                        }
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 8.dp
                    )
                ) {
                    Box {
                        when (state.card.type) {
                            CardType.QA -> QaCardContent(state)
                            CardType.MCQ -> McqCardContent(
                                card = state.card,
                                selectedChoice = selectedChoice,
                                onSelectChoice = { choice -> selectedChoice = choice }
                            )
                        }

                        val textToSpeak = when (state.card.type) {
                            CardType.QA -> if (state.showingAnswer) state.card.answer else state.card.question
                            CardType.MCQ -> buildString {
                                append(state.card.question)
                                state.card.choices.forEach { choice ->
                                    append(". ")
                                    append(choice)
                                }
                            }
                        }

                        // Auto-read whenever this state (new card, or answer just revealed)
                        // first appears — only while TTS is toggled on. Under hands-free
                        // assessment, start listening once the question (and, for MCQ, its
                        // choices) finish being read rather than waiting for a tap; once a QA
                        // card's answer has been read back after a hands-free verdict, pause
                        // briefly and advance automatically too — hands-free means no swipe.
                        LaunchedEffect(state) {
                            if (!ttsEnabled) return@LaunchedEffect
                            val shouldListen = handsFreeActive && micPermissionGranted &&
                                ((state.card.type == CardType.QA && !state.showingAnswer) ||
                                    (state.card.type == CardType.MCQ && selectedChoice == null))
                            val shouldAutoAdvance = handsFreeActive &&
                                state.card.type == CardType.QA && state.showingAnswer &&
                                (handsFreeState is HandsFreeState.Result || handsFreeState == HandsFreeState.Passed)
                            ttsController.speakAndAwait(textToSpeak)
                            if (shouldListen) {
                                when (state.card.type) {
                                    CardType.QA -> handsFreeController.startListeningForAnswer(
                                        question = state.card.question,
                                        correctAnswer = state.card.answer
                                    )
                                    CardType.MCQ -> handsFreeController.startListeningForChoice(
                                        state.card.choices
                                    )
                                }
                            } else if (shouldAutoAdvance) {
                                delay(HANDS_FREE_ADVANCE_DELAY_MS)
                                advance()
                            }
                        }

                        SpeakerButton(
                            onClick = { ttsController.speak(textToSpeak) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        )
                    }
                }
            }

            // Once a verdict is in, speak it, then move on — sequenced as a plain top-to-bottom
            // coroutine via speakAndAwait() so utterances don't talk over each other. QA reveals
            // the answer (which itself triggers the auto-advance above, once the answer has been
            // read back too); MCQ has no separate reveal step, so it speaks the verdict and the
            // correct answer here directly, then pauses before advancing.
            LaunchedEffect(handsFreeState) {
                when (val current = handsFreeState) {
                    is HandsFreeState.Result -> {
                        val verdictText = if (current.judgeResult.verdict == "CORRECT") "Correct." else "Incorrect."
                        ttsController.speakAndAwait(verdictText)
                        showAnswer = true
                    }
                    is HandsFreeState.ChoiceResult -> {
                        val matched = current.matchedChoice
                        if (matched != null) {
                            selectedChoice = matched
                            val verdictText = if (matched == currentCard.answer) "Correct." else "Incorrect."
                            ttsController.speakAndAwait(verdictText)
                            ttsController.speakAndAwait(currentCard.answer)
                            delay(HANDS_FREE_ADVANCE_DELAY_MS)
                            advance()
                        } else {
                            ttsController.speak("Sorry, I didn't catch a valid answer. Please tap your answer.")
                        }
                    }
                    HandsFreeState.Passed -> {
                        // No "Correct."/"Incorrect." — straight to the answer. QA reveals via
                        // showAnswer (the state-effect above speaks it and then advances, since
                        // Passed is included in that shouldAutoAdvance check); MCQ has no
                        // separate reveal step, so it's spoken and advanced right here.
                        when (currentCard.type) {
                            CardType.QA -> showAnswer = true
                            CardType.MCQ -> {
                                selectedChoice = currentCard.answer
                                ttsController.speakAndAwait(currentCard.answer)
                                delay(HANDS_FREE_ADVANCE_DELAY_MS)
                                advance()
                            }
                        }
                    }
                    else -> Unit
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            HandsFreeStatusText(handsFreeState, correctChoice = currentCard.answer)

            Text(
                text = when {
                    hasAnswered -> "Swipe to continue →"
                    currentCard.type == CardType.QA -> "Tap the card to reveal the answer"
                    else -> "Tap an answer"
                },
                color = Slate400,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun HandsFreeStatusText(state: HandsFreeState, correctChoice: String? = null) {
    val (text, color) = when (state) {
        HandsFreeState.Idle -> return
        HandsFreeState.Listening -> "🎤 Listening for your answer…" to Slate400
        is HandsFreeState.Processing -> "Grading \"${state.transcript}\"…" to Slate400
        is HandsFreeState.Result -> {
            val verdict = state.judgeResult.verdict
            "You said \"${state.transcript}\" — $verdict" to
                if (verdict == "CORRECT") Green500 else Red500
        }
        is HandsFreeState.ChoiceResult -> {
            val matched = state.matchedChoice
            if (matched == null) {
                "Didn't catch a valid answer — tap your answer instead." to Red500
            } else {
                "You said \"${state.transcript}\" → $matched" to
                    if (matched == correctChoice) Green500 else Red500
            }
        }
        HandsFreeState.Passed -> "Passed — here's the answer." to Slate400
        is HandsFreeState.Error -> "Hands-free: ${state.message}" to Red500
    }
    Text(text, color = color, fontSize = 13.sp, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun SpeakerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(36.dp),
        shape = CircleShape,
        color = Navy900,
        contentColor = Gray100
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("🔊", fontSize = 16.sp)
        }
    }
}

@Composable
fun QaCardContent(state: CardDisplayState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 220.dp, max = 380.dp)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (state.showingAnswer) {
                state.card.answer
            } else {
                state.card.question
            },
            fontSize = 28.sp,
            lineHeight = 36.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun McqCardContent(
    card: Flashcard,
    selectedChoice: String?,
    onSelectChoice: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = card.question,
            fontSize = 22.sp,
            lineHeight = 30.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        card.choices.forEach { choice ->
            val isSelected = choice == selectedChoice
            val isCorrectChoice = choice == card.answer
            val answered = selectedChoice != null

            val backgroundColor = when {
                !answered -> Navy900
                isCorrectChoice -> Green500
                isSelected -> Red500
                else -> Navy900
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !answered) {
                        onSelectChoice(choice)
                    },
                shape = RoundedCornerShape(12.dp),
                color = backgroundColor
            ) {
                Text(
                    text = choice,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    color = Gray100,
                    fontWeight = if (answered && (isCorrectChoice || isSelected)) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    }
                )
            }
        }

        if (selectedChoice != null && !card.explanation.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = card.explanation,
                fontSize = 14.sp,
                color = Gray100
            )
        }
    }
}
