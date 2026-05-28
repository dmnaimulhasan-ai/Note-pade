package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Note
import com.example.data.TodoItem
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.NoteColor
import com.example.ui.NoteViewModel
import com.example.ui.NoteViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

// Helper function to resolve adapted card colors for Light/Dark modes
fun getAdaptiveNoteColor(hexValue: String, isSystemDark: Boolean): Color {
    val lightColor = try {
        Color(android.graphics.Color.parseColor(hexValue))
    } catch (e: Exception) {
        Color.White
    }

    if (!isSystemDark) {
        return lightColor
    } else {
        return when (hexValue.uppercase()) {
            "#FFFFFF" -> Color(0xFF202124) // Default dark grey sheet surface
            "#F28B82" -> Color(0xFF5C2B29) // Muted Dark Red
            "#FBBC04" -> Color(0xFF614E1A) // Muted Dark Orange
            "#FFF475" -> Color(0xFF5B5418) // Muted Dark Yellow
            "#CCFF90" -> Color(0xFF345830) // Muted Dark Green
            "#A7FFEB" -> Color(0xFF16504B) // Muted Dark Teal
            "#CBF0F8" -> Color(0xFF2D555E) // Muted Dark Blue
            "#AECBFA" -> Color(0xFF1E3A5F) // Muted Dark Sky Blue
            "#D7AEFB" -> Color(0xFF422C5D) // Muted Dark Purple
            "#FDCFE8" -> Color(0xFF5B2245) // Muted Dark Pink
            "#E6C9A8" -> Color(0xFF443725) // Muted Dark Sand
            "#E8EAED" -> Color(0xFF3C4043) // Muted Dark Gray
            else -> {
                // Dim down bright colors for dark theme Compatibility
                Color(
                    red = lightColor.red * 0.35f,
                    green = lightColor.green * 0.35f,
                    blue = lightColor.blue * 0.35f,
                    alpha = 1.0f
                )
            }
        }
    }
}

// Simple representation of drawn paths inside the notes app
data class SimpleLine(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float = 6f
)

// Parse strokes from serialized string
fun parseDrawingLines(serialized: String): List<SimpleLine> {
    if (serialized.isEmpty() || !serialized.startsWith("SKETCH:")) return emptyList()
    val lines = mutableListOf<SimpleLine>()
    try {
        val data = serialized.substringAfter("SKETCH:")
        val lineSegments = data.split("|")
        for (segment in lineSegments) {
            if (segment.isEmpty()) continue
            val parts = segment.split(";")
            val colorStr = parts.getOrNull(0) ?: "#000000"
            val widthFloat = parts.getOrNull(1)?.toFloatOrNull() ?: 6f
            val color = try {
                Color(android.graphics.Color.parseColor(colorStr))
            } catch (e: Exception) {
                Color.Black
            }

            val points = mutableListOf<Offset>()
            for (i in 2 until parts.size) {
                val coords = parts[i].split(",")
                val x = coords.getOrNull(0)?.toFloatOrNull()
                val y = coords.getOrNull(1)?.toFloatOrNull()
                if (x != null && y != null) {
                    points.add(Offset(x, y))
                }
            }
            if (points.isNotEmpty()) {
                lines.add(SimpleLine(points, color, widthFloat))
            }
        }
    } catch (e: Exception) {
        // Safe fallback on corrupt parses
    }
    return lines
}

// Serialize list of simple lines into a note content string
fun serializeDrawingLines(lines: List<SimpleLine>): String {
    val builder = StringBuilder("SKETCH:")
    for (i in lines.indices) {
        val line = lines[i]
        // Save Hex Color
        val colorInt = android.graphics.Color.argb(
            (line.color.alpha * 255).toInt(),
            (line.color.red * 255).toInt(),
            (line.color.green * 255).toInt(),
            (line.color.blue * 255).toInt()
        )
        val hexColor = String.format("#%06X", 0xFFFFFF and colorInt)
        builder.append(hexColor).append(";").append(line.strokeWidth)
        for (point in line.points) {
            builder.append(";").append(point.x).append(",").append(point.y)
        }
        if (i < lines.lastIndex) {
            builder.append("|")
        }
    }
    return builder.toString()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: NoteViewModel = viewModel(
                    factory = NoteViewModelFactory(application)
                )
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: NoteViewModel) {
    val notes by viewModel.filteredNotes.collectAsStateWithLifecycle()
    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val activeLabelFilter by viewModel.selectedLabelFilter.collectAsStateWithLifecycle()
    val availableLabels by viewModel.availableLabels.collectAsStateWithLifecycle()
    val activeNote by viewModel.activeNote.collectAsStateWithLifecycle()

    val isDark = isSystemInDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main Grid View Screen
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                Column(
                    modifier = Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Google Keep Custom Search Bar
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search icon",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = {
                                    Text(
                                        text = "Search your notes",
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("search_input")
                            )

                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.setSearchQuery("") },
                                    modifier = Modifier.testTag("clear_search_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.toggleLayout() },
                                modifier = Modifier.testTag("layout_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = "Toggle Grid/List layout",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Colorful Google Notes avatar mimic
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF4B400)), // Warm Google yellow
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = "User Avatar",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Labels horizontal container filtering
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item {
                            FilterChip(
                                selected = activeLabelFilter == null,
                                onClick = { viewModel.selectLabelFilter(null) },
                                label = { Text("All Notes") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }

                        items(availableLabels) { label ->
                            FilterChip(
                                selected = activeLabelFilter == label,
                                onClick = { viewModel.selectLabelFilter(label) },
                                label = { Text(label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Label,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            },
            bottomBar = {
                // Bottom Rounded Keep styled bar holding tools
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Quick Action icon buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.startNewNote(isTodoList = true) },
                                modifier = Modifier.testTag("quick_checklist_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckBox,
                                    contentDescription = "New checklist note",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    // Start a drawing canvas note pre-defined
                                    val drawingNote = Note(
                                        title = "",
                                        content = "SKETCH:", // prefix indicating canvas doodle
                                        isTodoList = false,
                                        colorHex = "#CCFF90", // Cool Keep green theme by default
                                        isPinned = false,
                                        label = "Doodles"
                                    )
                                    viewModel.setActiveNote(drawingNote)
                                },
                                modifier = Modifier.testTag("quick_drawing_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Brush,
                                    contentDescription = "New drawing note",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = {
                                    // Simulated microphone/voice label note
                                    val voiceNote = Note(
                                        title = "Voice Reminder",
                                        content = "Spoken note reminder: Pick up groceries and pet supplies.",
                                        isTodoList = false,
                                        colorHex = "#CBF0F8", // Blue
                                        isPinned = false,
                                        label = "Audio"
                                    )
                                    viewModel.saveNote(voiceNote)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Add quick spoken note",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Prominent primary action button for notes
                        FloatingActionButton(
                            onClick = { viewModel.startNewNote(isTodoList = false) },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .testTag("add_note_fab")
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add normal note"
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Responsively decide columns
                val columnCount = if (isGridView) {
                    if (maxWidth > 900.dp) 4 else if (maxWidth > 600.dp) 3 else 2
                } else {
                    1
                }

                if (notes.isEmpty()) {
                    // Styled empty placeholder
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = "Lightbulb",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(90.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Notes you add appear here",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap + to add lists, sketches, and transcripts.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                    }
                } else {
                    val pinnedNotes = notes.filter { it.isPinned && !it.isArchived }
                    val activeNotes = notes.filter { !it.isPinned && !it.isArchived }
                    val archivedNotes = notes.filter { it.isArchived }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnCount),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // PINNED SECTION
                        if (pinnedNotes.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "PINNED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(pinnedNotes, key = { it.id }) { note ->
                                NoteCard(
                                    note = note,
                                    viewModel = viewModel,
                                    isDark = isDark,
                                    onClick = { viewModel.setActiveNote(note) }
                                )
                            }
                        }

                        // OTHERS / ALL SECTION
                        if (activeNotes.isNotEmpty()) {
                            if (pinnedNotes.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = "OTHERS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
                                    )
                                }
                            }
                            items(activeNotes, key = { it.id }) { note ->
                                NoteCard(
                                    note = note,
                                    viewModel = viewModel,
                                    isDark = isDark,
                                    onClick = { viewModel.setActiveNote(note) }
                                )
                            }
                        }

                        // ARCHIVED SECTION
                        if (archivedNotes.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Archive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "ARCHIVED",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            items(archivedNotes, key = { it.id }) { note ->
                                NoteCard(
                                    note = note,
                                    viewModel = viewModel,
                                    isDark = isDark,
                                    onClick = { viewModel.setActiveNote(note) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Note Editor Overlay (Dynamic Animated bottom slide screen)
        AnimatedVisibility(
            visible = activeNote != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(stiffness = 300f)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(stiffness = 300f)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            activeNote?.let { editingNote ->
                NoteEditorOverlay(
                    initialNote = editingNote,
                    viewModel = viewModel,
                    isDark = isDark,
                    onDismiss = { viewModel.setActiveNote(null) }
                )
            }
        }
    }
}

@Composable
fun NoteCard(
    note: Note,
    viewModel: NoteViewModel,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val cardBg = getAdaptiveNoteColor(note.colorHex, isDark)
    val textContrastColor = if (note.colorHex == "#FFFFFF" && !isDark) {
        MaterialTheme.colorScheme.onSurface
    } else if (isDark) {
        Color.White
    } else {
        Color(0xFF202124) // readable dark grey
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = if (note.colorHex == "#FFFFFF" && !isDark) {
                    MaterialTheme.colorScheme.outlineVariant
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("note_card_${note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Note Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = textContrastColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (note.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pinned Note",
                        tint = textContrastColor.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (note.title.isNotBlank() && (note.content.isNotBlank() || note.isTodoList)) {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Note Content Type Router
            if (note.isTodoList) {
                // Render small 3-item checklist summary
                val items = viewModel.parseTodoItems(note.todoItemsJson)
                val summaryItems = items.take(4)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (i in summaryItems.indices) {
                        val item = summaryItems[i]
                        if (i == 3 && items.size > 4) {
                            Text(
                                text = "+ ${items.size - 3} more items",
                                fontSize = 12.sp,
                                color = textContrastColor.copy(alpha = 0.5f),
                                modifier = Modifier.padding(start = 22.dp)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.isChecked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                                    contentDescription = null,
                                    tint = textContrastColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = item.text,
                                    fontSize = 13.sp,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                                    color = if (item.isChecked) textContrastColor.copy(alpha = 0.5f) else textContrastColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else if (note.content.startsWith("SKETCH:")) {
                // Embedded Sketch miniature canvas rendering
                val lines = remember(note.content) { parseDrawingLines(note.content) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(textContrastColor.copy(alpha = 0.05f))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (lines.isNotEmpty()) {
                            // Find bounds for scaling to card size
                            var minX = Float.MAX_VALUE
                            var maxX = Float.MIN_VALUE
                            var minY = Float.MAX_VALUE
                            var maxY = Float.MIN_VALUE
                            for (line in lines) {
                                for (p in line.points) {
                                    if (p.x < minX) minX = p.x
                                    if (p.x > maxX) maxX = p.x
                                    if (p.y < minY) minY = p.y
                                    if (p.y > maxY) maxY = p.y
                                }
                            }

                            val drawW = maxX - minX
                            val drawH = maxY - minY
                            val scaleX = if (drawW > 0) size.width / (drawW + 40f) else 1f
                            val scaleY = if (drawH > 0) size.height / (drawH + 40f) else 1f
                            val scale = minOf(scaleX, scaleY) * 0.85f

                            val offsetX = (size.width - (drawW * scale)) / 2f - (minX * scale)
                            val offsetY = (size.height - (drawH * scale)) / 2f - (minY * scale)

                            for (line in lines) {
                                if (line.points.size < 2) continue
                                val path = Path()
                                val start = line.points.first()
                                path.moveTo(start.x * scale + offsetX, start.y * scale + offsetY)
                                for (point in line.points.drop(1)) {
                                    path.lineTo(point.x * scale + offsetX, point.y * scale + offsetY)
                                }
                                drawPath(
                                    path = path,
                                    color = if (line.color == Color.Black && isDark) Color.White else line.color,
                                    style = Stroke(
                                        width = (line.strokeWidth * scale).coerceAtLeast(1.5f),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                    Text(
                        text = "Sketch",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = textContrastColor.copy(alpha = 0.45f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else {
                // Simple textual paragraph
                if (note.content.isNotBlank()) {
                    Text(
                        text = note.content,
                        fontSize = 14.sp,
                        color = textContrastColor.copy(alpha = 0.85f),
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Optional tags label chip rendering
            if (note.label.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(textContrastColor.copy(alpha = 0.1f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Label,
                            contentDescription = null,
                            tint = textContrastColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = note.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = textContrastColor.copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}

// Full Editor screen popup with Canvas and Checklist modules
@Composable
fun NoteEditorOverlay(
    initialNote: Note,
    viewModel: NoteViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialNote.title) }
    var content by remember { mutableStateOf(initialNote.content) }
    var colorHex by remember { mutableStateOf(initialNote.colorHex) }
    var isPinned by remember { mutableStateOf(initialNote.isPinned) }
    var isArchived by remember { mutableStateOf(initialNote.isArchived) }
    var label by remember { mutableStateOf(initialNote.label) }

    // Checklists lists
    var todoItems by remember {
        mutableStateOf(viewModel.parseTodoItems(initialNote.todoItemsJson))
    }

    // Canvas drawing states
    var drawingLines by remember {
        mutableStateOf(parseDrawingLines(initialNote.content))
    }
    var activePaintColor by remember { mutableStateOf(Color.Black) }
    var strokeWidth by remember { mutableStateOf(8f) }
    var isSketchpadEnabled by remember {
        mutableStateOf(initialNote.content.startsWith("SKETCH:"))
    }

    val activeBackground = getAdaptiveNoteColor(colorHex, isDark)
    val contrastedBodyText = if (isDark) {
        Color.White
    } else if (colorHex == "#FFFFFF") {
        MaterialTheme.colorScheme.onSurface
    } else {
        Color(0xFF202124)
    }

    val scaffoldBg = activeBackground

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scaffoldBg,
        topBar = {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val finalContent = if (isSketchpadEnabled) {
                                serializeDrawingLines(drawingLines)
                            } else {
                                content
                            }
                            val savedNote = initialNote.copy(
                                title = title.trim(),
                                content = finalContent.trim(),
                                todoItemsJson = viewModel.formatTodoItems(todoItems),
                                colorHex = colorHex,
                                isPinned = isPinned,
                                isArchived = isArchived,
                                label = label.trim(),
                                timestamp = System.currentTimeMillis()
                            )
                            viewModel.saveNote(savedNote)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Save and Back",
                            tint = contrastedBodyText
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isPinned = !isPinned },
                        modifier = Modifier.testTag("pin_button")
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (isPinned) "Unpin Note" else "Pin Note",
                            tint = contrastedBodyText
                        )
                    }

                    IconButton(
                        onClick = { isArchived = !isArchived },
                        modifier = Modifier.testTag("archive_button")
                    ) {
                        Icon(
                            imageVector = if (isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
                            contentDescription = if (isArchived) "Unarchive note" else "Archive note",
                            tint = contrastedBodyText
                        )
                    }

                    if (initialNote.id != 0) {
                        IconButton(
                            onClick = {
                                viewModel.deleteNote(initialNote)
                                onDismiss()
                            },
                            modifier = Modifier.testTag("delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete note",
                                tint = contrastedBodyText
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .fillMaxWidth()
                    .background(scaffoldBg)
                    .padding(12.dp)
            ) {
                // Horizontal scrolling picker of 12 Google Keep pastel theme background colors
                Text(
                    text = "Background Color",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = contrastedBodyText.copy(alpha = 0.65f),
                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    items(viewModel.keepColors) { colorObj ->
                        val itemColorRepresentation = getAdaptiveNoteColor(colorObj.hexValue, isDark)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(itemColorRepresentation)
                                .border(
                                    width = if (colorHex == colorObj.hexValue) 3.dp else 1.dp,
                                    color = if (colorHex == colorObj.hexValue) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        contrastedBodyText.copy(alpha = 0.35f)
                                    },
                                    shape = CircleShape
                                )
                                .clickable { colorHex = colorObj.hexValue }
                        )
                    }
                }

                Divider(color = contrastedBodyText.copy(alpha = 0.15f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category / Label configuration field
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Label,
                            contentDescription = "Label icon",
                            tint = contrastedBodyText.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        TextField(
                            value = label,
                            onValueChange = { label = it },
                            placeholder = {
                                Text(
                                    text = "Add label...",
                                    fontSize = 13.sp,
                                    color = contrastedBodyText.copy(alpha = 0.5f)
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Format modification shortcut indicators (Toggles check/drawing)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                // Toggle Sketch pad mode
                                isSketchpadEnabled = !isSketchpadEnabled
                                if (isSketchpadEnabled && !content.startsWith("SKETCH:")) {
                                    content = "SKETCH:"
                                } else if (!isSketchpadEnabled && content.startsWith("SKETCH:")) {
                                    content = ""
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Brush,
                                contentDescription = "Toggle drawing pad",
                                tint = if (isSketchpadEnabled) MaterialTheme.colorScheme.primary else contrastedBodyText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                // Live timestamp footer
                val formattedDate = remember(initialNote.timestamp) {
                    try {
                        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                        sdf.format(Date(initialNote.timestamp))
                    } catch (e: Exception) {
                        "Edited Just Now"
                    }
                }
                Text(
                    text = "Edited $formattedDate",
                    fontSize = 11.sp,
                    color = contrastedBodyText.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            // Title Text Input Field
            TextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text(
                        text = "Title",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = contrastedBodyText.copy(alpha = 0.45f)
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = contrastedBodyText
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("title_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Body Context Mode Selector
            if (initialNote.isTodoList) {
                // Renders the rich interactive checklist list edit interface
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Loop checkboxes
                    Box(modifier = Modifier.weight(1f)) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(todoItems, key = { it.id }) { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = item.isChecked,
                                        onCheckedChange = { isChecked ->
                                            todoItems = todoItems.map {
                                                if (it.id == item.id) it.copy(isChecked = isChecked) else it
                                            }
                                        }
                                    )
                                    TextField(
                                        value = item.text,
                                        onValueChange = { newTxt ->
                                            todoItems = todoItems.map {
                                                if (it.id == item.id) it.copy(text = newTxt) else it
                                            }
                                        },
                                        textStyle = LocalTextStyle.current.copy(
                                            fontSize = 15.sp,
                                            color = if (item.isChecked) contrastedBodyText.copy(alpha = 0.45f) else contrastedBodyText,
                                            textDecoration = if (item.isChecked) TextDecoration.LineThrough else null
                                        ),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            todoItems = todoItems.filter { it.id != item.id }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove item",
                                            tint = contrastedBodyText.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }

                            // Add checklist item entry layout button
                            item {
                                TextButton(
                                    onClick = {
                                        val newId = UUID.randomUUID().toString()
                                        todoItems = todoItems + TodoItem(id = newId, text = "", isChecked = false)
                                    },
                                    modifier = Modifier.padding(start = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("List item", fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            } else if (isSketchpadEnabled) {
                // DRAWING CANVAS SKETCH MODULE
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SKETCH CANVAS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = contrastedBodyText.copy(alpha = 0.6f)
                        )

                        // Brush setup and tool options
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Canvas brush sizes selection
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(contrastedBodyText.copy(alpha = 0.08f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Brush Size: ",
                                    fontSize = 10.sp,
                                    color = contrastedBodyText.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "${strokeWidth.toInt()}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contrastedBodyText
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Button(
                                    onClick = { strokeWidth = if (strokeWidth > 4f) strokeWidth - 4f else 4f },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = contrastedBodyText.copy(alpha = 0.2f))
                                ) {
                                    Text("-", fontSize = 10.sp, color = contrastedBodyText)
                                }
                                Spacer(modifier = Modifier.width(2.dp))
                                Button(
                                    onClick = { strokeWidth += 4f },
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.size(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = contrastedBodyText.copy(alpha = 0.2f))
                                ) {
                                    Text("+", fontSize = 10.sp, color = contrastedBodyText)
                                }
                            }

                            TextButton(
                                onClick = { drawingLines = emptyList() },
                                colors = ButtonDefaults.textButtonColors(contentColor = contrastedBodyText)
                            ) {
                                Text("Clear", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Multi-color inline palette choice for Drawing brush pen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val paintColors = listOf(Color.Black, Color.Red, Color.Blue, Color(0xFF4CAF50), Color(0xFFFF9800), Color(0xFFE91E63))
                        for (pcolor in paintColors) {
                            val displayColor = if (pcolor == Color.Black && isDark) Color.White else pcolor
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(displayColor)
                                    .border(
                                        width = if (activePaintColor == pcolor) 2.dp else 1.dp,
                                        color = if (activePaintColor == pcolor) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { activePaintColor = pcolor }
                            )
                        }
                    }

                    // Interactive Gesturing Drawing Board Block
                    var currentLinePoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(contrastedBodyText.copy(alpha = 0.05f))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentLinePoints = listOf(offset)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        currentLinePoints = currentLinePoints + change.position
                                    },
                                    onDragEnd = {
                                        if (currentLinePoints.isNotEmpty()) {
                                            drawingLines = drawingLines + SimpleLine(
                                                points = currentLinePoints,
                                                color = activePaintColor,
                                                strokeWidth = strokeWidth
                                            )
                                            currentLinePoints = emptyList()
                                        }
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Render completed drawing lines
                            for (line in drawingLines) {
                                if (line.points.size < 2) continue
                                val path = Path()
                                path.moveTo(line.points.first().x, line.points.first().y)
                                for (point in line.points.drop(1)) {
                                    path.lineTo(point.x, point.y)
                                }
                                drawPath(
                                    path = path,
                                    color = if (line.color == Color.Black && isDark) Color.White else line.color,
                                    style = Stroke(
                                        width = line.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }

                            // Render line in active progress
                            if (currentLinePoints.size >= 2) {
                                val path = Path()
                                path.moveTo(currentLinePoints.first().x, currentLinePoints.first().y)
                                for (point in currentLinePoints.drop(1)) {
                                    path.lineTo(point.x, point.y)
                                }
                                drawPath(
                                    path = path,
                                    color = if (activePaintColor == Color.Black && isDark) Color.White else activePaintColor,
                                    style = Stroke(
                                        width = strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                // Default rich-text notepad editing block
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = {
                        Text(
                            text = "Note",
                            fontSize = 16.sp,
                            color = contrastedBodyText.copy(alpha = 0.45f)
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        color = contrastedBodyText
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("content_input")
                )
            }
        }
    }
}
