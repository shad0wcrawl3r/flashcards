package dev.shadowcrawler.flashcards.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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

/**
 * Pre-filled with the field-selector paraphrase example from the answer-matching discussion.
 * Labeled output fields (VERDICT/CONFIDENCE/REASON) instead of freeform prose so the response
 * can be parsed reliably — small models especially tend to bury or reword a freeform verdict,
 * which is exactly what made the earlier MATCH/No_Match casing drift hard to compare across
 * runs. CONFIDENCE is a self-reported signal for telling "the model is unsure" apart from
 * "the model is confidently wrong" when runs disagree.
 */
private const val DEFAULT_BENCHMARK_PROMPT = """Grade a spoken flashcard answer against the correct answer.

Question: What is a field selector?
Correct answer: A query mechanism that filters Kubernetes resources by resource fields.
Spoken answer: something that matches against the fields set in the spec or manifest

Grading rule: CORRECT if the spoken answer captures the same core idea as the correct answer, even worded very differently. INCORRECT if it misses the key idea, is factually wrong, or is too vague to tell.

Respond in exactly this format and nothing else:
VERDICT: CORRECT or INCORRECT
CONFIDENCE: LOW, MEDIUM, or HIGH
REASON: one short sentence"""

/** Pulls the value after "VERDICT:" from the first matching line, if the model followed format. */
private fun extractVerdict(response: String): String? {
    val line = response.lineSequence().firstOrNull { it.trim().startsWith("VERDICT", ignoreCase = true) }
        ?: return null
    return line.substringAfter(":", "").trim().uppercase().takeIf { it.isNotEmpty() }
}

@Composable
fun LlmBenchmarkScreen(
    onBack: () -> Unit,
    onOpenModelDownload: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: LlmBenchmarkViewModel = viewModel(
        factory = LlmBenchmarkViewModelFactory(context)
    )
    val availableModels by viewModel.availableModels.collectAsState()
    val modelLoadState by viewModel.modelLoadState.collectAsState()
    val generationState by viewModel.generationState.collectAsState()
    var promptText by remember { mutableStateOf(DEFAULT_BENCHMARK_PROMPT) }
    var temperatureText by remember { mutableStateOf("0.8") }
    var topKText by remember { mutableStateOf("40") }
    var seedText by remember { mutableStateOf("0") }
    var runsText by remember { mutableStateOf("3") }

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
                text = "LLM Benchmark",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Gray100
            )

            Text(
                text = "Search Hugging Face and download a model on-device, or adb push a " +
                    ".task file yourself to:\n${viewModel.modelsDirPath}",
                color = Slate400,
                fontSize = 12.sp
            )

            Button(onClick = onOpenModelDownload, modifier = Modifier.fillMaxWidth()) {
                Text("Find & download models")
            }

            Button(onClick = { viewModel.refreshModels() }, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh models")
            }

            if (availableModels.isEmpty()) {
                Text("No .task models found yet.", color = Slate400)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableModels.forEach { model ->
                        val isLoaded = (modelLoadState as? ModelLoadState.Loaded)?.modelName == model.name
                        ModelRow(
                            model = model,
                            isLoaded = isLoaded,
                            onSelect = { viewModel.loadModel(model) }
                        )
                    }
                }
            }

            when (val current = modelLoadState) {
                ModelLoadState.NotLoaded -> Text("Select a model to load it.", color = Slate400)
                ModelLoadState.Loading -> LoadingRow("Loading model…")
                is ModelLoadState.Loaded -> Text("${current.modelName} ready.", color = Green500)
                is ModelLoadState.Error -> Text(current.message, color = Red500)
            }

            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                label = { Text("Prompt") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp)
            )

            Text(
                text = "Sampling — topK 1 makes decoding greedy/deterministic regardless of temperature or seed. " +
                    "Runs repeats the prompt with seed, seed+1, seed+2… so you can see the actual spread.",
                color = Slate400,
                fontSize = 12.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(label = "Temp", value = temperatureText, onValueChange = { temperatureText = it }, modifier = Modifier.weight(1f))
                NumberField(label = "TopK", value = topKText, onValueChange = { topKText = it }, modifier = Modifier.weight(1f))
                NumberField(label = "Seed", value = seedText, onValueChange = { seedText = it }, modifier = Modifier.weight(1f))
                NumberField(label = "Runs", value = runsText, onValueChange = { runsText = it }, modifier = Modifier.weight(1f))
            }

            val modelReady = modelLoadState is ModelLoadState.Loaded
            val generating = generationState is GenerationState.Generating
            Button(
                onClick = {
                    viewModel.runPrompt(
                        prompt = promptText,
                        temperature = temperatureText.toFloatOrNull() ?: 0.8f,
                        topK = topKText.toIntOrNull() ?: 40,
                        baseSeed = seedText.toIntOrNull() ?: 0,
                        repeatCount = (runsText.toIntOrNull() ?: 1).coerceIn(1, 10)
                    )
                },
                enabled = modelReady && !generating && promptText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Run")
            }

            when (val current = generationState) {
                GenerationState.Idle -> Unit
                is GenerationState.Generating -> LoadingRow("Generating ${current.completed + 1} of ${current.total}…")
                is GenerationState.Result -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (current.runs.size > 1) {
                            VerdictSummary(current.runs.map { extractVerdict(it.response) })
                        }
                        current.runs.forEachIndexed { index, run ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Run ${index + 1} · ${run.elapsedMs} ms", color = Slate400, fontSize = 12.sp)
                                    extractVerdict(run.response)?.let { VerdictBadge(it) }
                                }
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Navy900
                                ) {
                                    Text(
                                        text = run.response,
                                        modifier = Modifier.padding(12.dp),
                                        color = Gray100,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
                is GenerationState.Error -> Text(current.message, color = Red500)
            }
        }
    }
}

/** Distinguishes "the model agrees with itself" from "it flip-flops" at a glance. */
@Composable
private fun VerdictSummary(verdicts: List<String?>) {
    val parsed = verdicts.filterNotNull()
    if (parsed.isEmpty()) {
        Text("Model didn't follow the VERDICT: format in any run.", color = Red500, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        return
    }
    val distinct = parsed.distinct()
    val text = if (distinct.size == 1) {
        "Consistent across ${parsed.size} run${if (parsed.size == 1) "" else "s"}: ${distinct.first()}"
    } else {
        val counts = parsed.groupingBy { it }.eachCount().entries.joinToString(", ") { "${it.value}× ${it.key}" }
        "Inconsistent across runs: $counts"
    }
    Text(text, color = if (distinct.size == 1) Green500 else Red500, fontSize = 13.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun VerdictBadge(verdict: String) {
    val color = when {
        verdict.contains("INCORRECT") || verdict.contains("NO_MATCH") || verdict.contains("NO MATCH") -> Red500
        verdict.contains("CORRECT") || verdict.contains("MATCH") -> Green500
        else -> Slate400
    }
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(
            text = verdict,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = Gray100,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun LoadingRow(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Blue600)
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, color = Slate400)
    }
}

@Composable
private fun ModelRow(
    model: LocalLlmModel,
    isLoaded: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isLoaded) Blue600 else Navy800
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(model.name, color = Gray100, fontSize = 14.sp)
            Text("${model.sizeMb} MB", color = Slate400, fontSize = 12.sp)
        }
    }
}
