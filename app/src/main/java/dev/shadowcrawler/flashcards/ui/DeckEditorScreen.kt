package dev.shadowcrawler.flashcards.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.shadowcrawler.flashcards.model.CardType
import dev.shadowcrawler.flashcards.model.FlashcardDatabase
import dev.shadowcrawler.flashcards.ui.theme.Blue600
import dev.shadowcrawler.flashcards.ui.theme.Gray100
import dev.shadowcrawler.flashcards.ui.theme.Green500
import dev.shadowcrawler.flashcards.ui.theme.Navy800
import dev.shadowcrawler.flashcards.ui.theme.Navy900
import dev.shadowcrawler.flashcards.ui.theme.Red500
import dev.shadowcrawler.flashcards.ui.theme.Slate400

@Composable
fun DeckEditorScreen(
    database: FlashcardDatabase,
    deckId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val viewModel: DeckEditorViewModel = viewModel(
        factory = DeckEditorViewModelFactory(database, deckId)
    )
    val state by viewModel.state.collectAsState()

    var editingCard by remember { mutableStateOf<DraftCard?>(null) }
    var cardPendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    if (state.saved) return

    BackHandler(enabled = editingCard != null) { editingCard = null }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue600)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BackButton(text = "← Back", onClick = onBack)
                    Text(
                        text = if (state.canSave) "Save" else "",
                        color = Green500,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(enabled = state.canSave && !state.isSaving) { viewModel.save() }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = if (state.isNewDeck) "New Deck" else "Edit Deck",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Gray100
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = state.deckName,
                            onValueChange = viewModel::updateName,
                            label = { Text("Deck name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = viewModel::updateDescription,
                            label = { Text("Description (optional)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = state.tagsText,
                            onValueChange = viewModel::updateTagsText,
                            label = { Text("Tags, comma separated (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cards (${state.cards.size})",
                                color = Gray100,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = { editingCard = DraftCard() }) {
                                Text("+ Add card", color = Blue600, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (state.cards.isEmpty()) {
                        item {
                            EmptyCardsPrompt(onAddCard = { editingCard = DraftCard() })
                        }
                    } else {
                        items(state.cards, key = { it.localId }) { card ->
                            DraftCardRow(
                                card = card,
                                onClick = { editingCard = card },
                                onDelete = { cardPendingDelete = card.localId }
                            )
                        }
                    }

                    if (!state.canSave && state.cards.isNotEmpty()) {
                        item {
                            Text(
                                text = "Every card needs a question, at least two choices " +
                                    "(or a Q&A answer), and a marked correct answer.",
                                color = Slate400,
                                fontSize = 12.sp
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }

            FloatingActionButton(
                onClick = { editingCard = DraftCard() },
                containerColor = Blue600,
                contentColor = Gray100,
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
            ) {
                Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        editingCard?.let { card ->
            McqCardEditor(
                initialCard = card,
                isNewCard = state.cards.none { it.localId == card.localId },
                onSave = { updated ->
                    viewModel.upsertCard(updated)
                    editingCard = null
                },
                onCancel = { editingCard = null },
                onDelete = {
                    viewModel.removeCard(card.localId)
                    editingCard = null
                }
            )
        }
    }

    cardPendingDelete?.let { localId ->
        AlertDialog(
            onDismissRequest = { cardPendingDelete = null },
            title = { Text("Delete this card?") },
            text = { Text("This can't be undone.", color = Slate400) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeCard(localId)
                    cardPendingDelete = null
                }) { Text("Delete", color = Red500) }
            },
            dismissButton = {
                TextButton(onClick = { cardPendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Something went wrong") },
            text = { Text(message, color = Slate400) },
            confirmButton = {
                TextButton(onClick = onBack) { Text("OK") }
            }
        )
    }
}

@Composable
private fun EmptyCardsPrompt(onAddCard: () -> Unit) {
    Surface(
        onClick = onAddCard,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Navy800
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📝", fontSize = 32.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "No cards yet",
                color = Gray100,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                "Tap to add your first multiple-choice question.",
                color = Slate400,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun DraftCardRow(
    card: DraftCard,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Navy800
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (card.type == CardType.MCQ) Blue600 else Navy900
                    ) {
                        Text(
                            text = if (card.type == CardType.MCQ) "MCQ" else "Q/A",
                            color = Gray100,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (!card.isValid) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("⚠ incomplete", color = Red500, fontSize = 11.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = card.summary,
                    color = Gray100,
                    fontSize = 15.sp,
                    maxLines = 2
                )
                if (card.type == CardType.MCQ && card.correctChoiceIndex != null) {
                    Text(
                        text = "✓ ${card.choices.getOrNull(card.correctChoiceIndex).orEmpty()}",
                        color = Green500,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = "🗑",
                fontSize = 16.sp,
                modifier = Modifier
                    .clickable(onClick = onDelete)
                    .padding(8.dp)
            )
        }
    }
}
