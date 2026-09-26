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
import dev.shadowcrawler.flashcards.ui.laya.LayaEngine
import dev.shadowcrawler.flashcards.ui.theme.Blue600
import dev.shadowcrawler.flashcards.ui.theme.Gray100
import dev.shadowcrawler.flashcards.ui.theme.Green500
import dev.shadowcrawler.flashcards.ui.theme.Navy800
import dev.shadowcrawler.flashcards.ui.theme.Navy900
import dev.shadowcrawler.flashcards.ui.theme.Red500
import dev.shadowcrawler.flashcards.ui.theme.Slate400

/** The project's original motivating case: a correct paraphrase with almost no lexical overlap. */
private const val DEFAULT_QUESTION = "What is a field selector?"
private const val DEFAULT_CORRECT_ANSWER =
    "A query mechanism that filters Kubernetes resources by resource fields."
private const val DEFAULT_SPOKEN_ANSWER =
    "something that matches against the fields set in the spec or manifest"

@Composable
fun LayaBenchmarkScreen(
    onBack: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val viewModel: LayaBenchmarkViewModel = viewModel(
        factory = LayaBenchmarkViewModelFactory(context)
    )
    val modelState by viewModel.modelState.collectAsState()
    val engineState by viewModel.engineState.collectAsState()
    val judgeState by viewModel.judgeState.collectAsState()
    val hfToken by settingsViewModel.huggingFaceToken.collectAsState()

    var questionText by remember { mutableStateOf(DEFAULT_QUESTION) }
    var correctAnswerText by remember { mutableStateOf(DEFAULT_CORRECT_ANSWER) }
    var spokenAnswerText by remember { mutableStateOf(DEFAULT_SPOKEN_ANSWER) }
    var backend by remember { mutableStateOf(LayaEngine.Backend.GPU) }

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
                text = "Laya Judge",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Gray100
            )

            Text(
                text = "litert-community/Laya-Multilingual-LiteRT — a typed-decision model, not a " +
                    "freeform LLM. Always returns a choice + probabilities, no output parsing or " +
                    "sampling noise.\n${viewModel.modelDirPath}",
                color = Slate400,
                fontSize = 12.sp
            )

            when (val current = modelState) {
                LayaModelState.NotDownloaded -> {
                    Button(
                        onClick = { viewModel.downloadModel(hfToken) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Download Laya model (~680 MB)")
                    }
                }
                is LayaModelState.Downloading -> {
                    val progressText = if (current.totalBytes > 0) {
                        val pct = (100.0 * current.bytesDownloaded / current.totalBytes).toInt()
                        "${current.filename} ($pct%)"
                    } else {
                        current.filename
                    }
                    LoadingRow("Downloading file ${current.fileIndex}/${current.totalFiles}: $progressText")
                    Button(onClick = { viewModel.cancelDownload() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }
                LayaModelState.Ready -> {
                    Text("Model files ready.", color = Green500)
                }
                is LayaModelState.Error -> {
                    Text(current.message, color = Red500)
                    Button(
                        onClick = { viewModel.downloadModel(hfToken) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry download")
                    }
                }
            }

            if (modelState == LayaModelState.Ready) {
                Text("Backend", color = Slate400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackendButton(
                        label = "GPU",
                        selected = backend == LayaEngine.Backend.GPU,
                        onClick = { backend = LayaEngine.Backend.GPU }
                    )
                    BackendButton(
                        label = "CPU",
                        selected = backend == LayaEngine.Backend.CPU,
                        onClick = { backend = LayaEngine.Backend.CPU }
                    )
                }

                Button(
                    onClick = { viewModel.loadEngine(backend) },
                    enabled = engineState !is LayaEngineState.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load model")
                }

                when (val current = engineState) {
                    LayaEngineState.NotLoaded -> Text("Load the model to start judging.", color = Slate400)
                    LayaEngineState.Loading -> LoadingRow("Compiling graphs on $backend…")
                    LayaEngineState.Loaded -> Text("Ready on $backend.", color = Green500)
                    is LayaEngineState.Error -> Text(current.message, color = Red500)
                }
            }

            OutlinedTextField(
                value = questionText,
                onValueChange = { questionText = it },
                label = { Text("Question") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = correctAnswerText,
                onValueChange = { correctAnswerText = it },
                label = { Text("Correct answer") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
            )
            OutlinedTextField(
                value = spokenAnswerText,
                onValueChange = { spokenAnswerText = it },
                label = { Text("Spoken answer") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
            )

            val engineReady = engineState is LayaEngineState.Loaded
            val judging = judgeState is LayaJudgeState.Judging
            Button(
                onClick = {
                    viewModel.judge(questionText, correctAnswerText, spokenAnswerText, backend)
                },
                enabled = engineReady && !judging && questionText.isNotBlank() && correctAnswerText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Judge")
            }

            when (val current = judgeState) {
                LayaJudgeState.Idle -> Unit
                LayaJudgeState.Judging -> LoadingRow("Judging…")
                is LayaJudgeState.Result -> JudgeResultCard(current.result)
                is LayaJudgeState.Error -> Text(current.message, color = Red500)
            }
        }
    }
}

@Composable
private fun JudgeResultCard(result: LayaJudgeResult) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VerdictBadge(result.verdict)
            Text("${result.elapsedMs.toInt()} ms", color = Slate400, fontSize = 12.sp)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = Navy900
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                result.probabilities.forEach { (label, probability) ->
                    Text(
                        "$label: ${"%.4f".format(probability)}",
                        color = Gray100,
                        fontSize = 14.sp
                    )
                }
                Text(
                    "confidence: ${"%.4f".format(result.confidence)}  ·  act_probability: ${"%.4f".format(result.actProbability)}",
                    color = Slate400,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun BackendButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Blue600 else Navy800
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = Gray100,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
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
private fun VerdictBadge(verdict: String) {
    val color = if (verdict == "CORRECT") Green500 else Red500
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
