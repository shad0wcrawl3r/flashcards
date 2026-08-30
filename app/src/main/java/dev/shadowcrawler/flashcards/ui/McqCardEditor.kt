package dev.shadowcrawler.flashcards.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shadowcrawler.flashcards.model.CardType
import dev.shadowcrawler.flashcards.ui.theme.Blue600
import dev.shadowcrawler.flashcards.ui.theme.Gray100
import dev.shadowcrawler.flashcards.ui.theme.Green500
import dev.shadowcrawler.flashcards.ui.theme.Navy800
import dev.shadowcrawler.flashcards.ui.theme.Navy900
import dev.shadowcrawler.flashcards.ui.theme.Red500
import dev.shadowcrawler.flashcards.ui.theme.Slate400

private const val MIN_CHOICES = 2
private const val MAX_CHOICES = 6

private val difficultyLevels = listOf("Easy" to 5, "Medium" to 10, "Hard" to 15)

/**
 * Full-screen card composer. Multiple choice is the default and the richest path — tapping the
 * marker beside a choice both selects and validates it as the correct answer in one motion.
 */
@Composable
fun McqCardEditor(
    initialCard: DraftCard,
    isNewCard: Boolean,
    onSave: (DraftCard) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    var draft by remember(initialCard.localId) { mutableStateOf(initialCard) }
    var attemptedSave by remember(initialCard.localId) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Navy900
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onCancel) { Text("Cancel", color = Slate400) }
                Text(
                    text = if (isNewCard) "New Card" else "Edit Card",
                    color = Gray100,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Save",
                    color = Green500,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable {
                            attemptedSave = true
                            if (draft.isValid) onSave(draft)
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    CardTypeToggle(
                        type = draft.type,
                        onTypeChange = { newType -> draft = draft.copy(type = newType) }
                    )
                }

                item {
                    Column {
                        Text("Question", color = Slate400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = draft.question,
                            onValueChange = { draft = draft.copy(question = it) },
                            placeholder = { Text("Type your question…") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        if (attemptedSave && draft.question.isBlank()) {
                            FieldError("A question is required.")
                        }
                    }
                }

                when (draft.type) {
                    CardType.MCQ -> {
                        item {
                            Text(
                                "Answer choices",
                                color = Slate400,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        itemsIndexed(draft.choices) { index, choiceText ->
                            ChoiceRow(
                                text = choiceText,
                                isCorrect = draft.correctChoiceIndex == index,
                                canRemove = draft.choices.size > MIN_CHOICES,
                                onTextChange = { updated ->
                                    draft = draft.copy(
                                        choices = draft.choices.toMutableList().apply { set(index, updated) }
                                    )
                                },
                                onMarkCorrect = { draft = draft.copy(correctChoiceIndex = index) },
                                onRemove = {
                                    val newChoices = draft.choices.toMutableList().apply { removeAt(index) }
                                    val newCorrect = when {
                                        draft.correctChoiceIndex == null -> null
                                        draft.correctChoiceIndex == index -> null
                                        draft.correctChoiceIndex!! > index -> draft.correctChoiceIndex!! - 1
                                        else -> draft.correctChoiceIndex
                                    }
                                    draft = draft.copy(choices = newChoices, correctChoiceIndex = newCorrect)
                                }
                            )
                        }

                        item {
                            if (draft.choices.size < MAX_CHOICES) {
                                TextButton(onClick = {
                                    draft = draft.copy(choices = draft.choices + "")
                                }) {
                                    Text("+ Add choice", color = Blue600, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (attemptedSave) {
                                val nonBlank = draft.choices.count { it.isNotBlank() }
                                val correctIndex = draft.correctChoiceIndex
                                if (nonBlank < MIN_CHOICES) {
                                    FieldError("Add at least $MIN_CHOICES answer choices.")
                                } else if (correctIndex == null ||
                                    draft.choices.getOrNull(correctIndex).orEmpty().isBlank()
                                ) {
                                    FieldError("Tap a marker to select the correct answer.")
                                }
                            }
                        }
                    }

                    CardType.QA -> {
                        item {
                            Column {
                                Text("Answer", color = Slate400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = draft.qaAnswer,
                                    onValueChange = { draft = draft.copy(qaAnswer = it) },
                                    placeholder = { Text("Type the answer…") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2
                                )
                                if (attemptedSave && draft.qaAnswer.isBlank()) {
                                    FieldError("An answer is required.")
                                }
                            }
                        }
                    }
                }

                item {
                    Column {
                        Text(
                            "Explanation (optional)",
                            color = Slate400,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = draft.explanation,
                            onValueChange = { draft = draft.copy(explanation = it) },
                            placeholder = { Text("Shown after answering, to reinforce why.") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                    }
                }

                item {
                    Column {
                        Text("Difficulty", color = Slate400, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            difficultyLevels.forEach { (label, value) ->
                                DifficultyChip(
                                    label = label,
                                    selected = draft.difficulty == value,
                                    onClick = { draft = draft.copy(difficulty = value) }
                                )
                            }
                        }
                    }
                }

                if (!isNewCard) {
                    item {
                        TextButton(onClick = onDelete) {
                            Text("Delete card", color = Red500)
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }

            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Button(
                    onClick = {
                        attemptedSave = true
                        if (draft.isValid) onSave(draft)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isNewCard) "Add Card" else "Save Changes")
                }
            }
        }
    }
}

@Composable
private fun CardTypeToggle(
    type: CardType,
    onTypeChange: (CardType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Navy800),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TypeToggleOption(
            label = "Multiple Choice",
            selected = type == CardType.MCQ,
            modifier = Modifier.weight(1f),
            onClick = { onTypeChange(CardType.MCQ) }
        )
        TypeToggleOption(
            label = "Q & A",
            selected = type == CardType.QA,
            modifier = Modifier.weight(1f),
            onClick = { onTypeChange(CardType.QA) }
        )
    }
}

@Composable
private fun TypeToggleOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Blue600 else Navy800
    ) {
        Text(
            text = label,
            color = Gray100,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        )
    }
}

@Composable
private fun ChoiceRow(
    text: String,
    isCorrect: Boolean,
    canRemove: Boolean,
    onTextChange: (String) -> Unit,
    onMarkCorrect: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onMarkCorrect,
            shape = CircleShape,
            color = if (isCorrect) Green500 else Navy800,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isCorrect) {
                    Text("✓", color = Gray100, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Choice text") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        if (canRemove) {
            Text(
                text = "✕",
                color = Slate400,
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(10.dp)
            )
        }
    }
}

@Composable
private fun DifficultyChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) Blue600 else Navy800
    ) {
        Text(
            text = label,
            color = Gray100,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun FieldError(message: String) {
    Text(
        text = message,
        color = Red500,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 4.dp)
    )
}
