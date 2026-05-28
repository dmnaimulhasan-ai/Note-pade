package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository
    private val converters = Converters()

    init {
        val database = NoteDatabase.getDatabase(application)
        repository = NoteRepository(database.noteDao())
    }

    // Is Grid View or List View
    private val _isGridView = MutableStateFlow(true)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    // Search Query state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Selected Label filter state
    private val _selectedLabelFilter = MutableStateFlow<String?>(null)
    val selectedLabelFilter: StateFlow<String?> = _selectedLabelFilter.asStateFlow()

    // Raw notes from DB
    val allNotes: StateFlow<List<Note>> = repository.allNotes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Filtered Notes
    val filteredNotes: StateFlow<List<Note>> = combine(
        allNotes,
        _searchQuery,
        _selectedLabelFilter
    ) { notes, query, label ->
        notes.filter { note ->
            val matchesQuery = query.isEmpty() ||
                    note.title.contains(query, ignoreCase = true) ||
                    note.content.contains(query, ignoreCase = true) ||
                    note.label.contains(query, ignoreCase = true) ||
                    (note.isTodoList && note.todoItemsJson.contains(query, ignoreCase = true))

            val matchesLabel = label == null || note.label.equals(label, ignoreCase = true)
            matchesQuery && matchesLabel
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active Note being edited or created. If null, show main grid/list
    private val _activeNote = MutableStateFlow<Note?>(null)
    val activeNote: StateFlow<Note?> = _activeNote.asStateFlow()

    // Unique labels list across all saved notes (dynamic tags)
    val availableLabels: StateFlow<List<String>> = allNotes.map { notes ->
        notes.map { it.label }.filter { it.isNotBlank() }.distinct().sorted()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun toggleLayout() {
        _isGridView.value = !_isGridView.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectLabelFilter(label: String?) {
        _selectedLabelFilter.value = label
    }

    fun setActiveNote(note: Note?) {
        _activeNote.value = note
    }

    fun startNewNote(isTodoList: Boolean = false) {
        _activeNote.value = Note(
            title = "",
            content = "",
            isTodoList = isTodoList,
            colorHex = "#FFFFFF",
            isPinned = false,
            isArchived = false,
            label = ""
        )
    }

    fun saveNote(note: Note) {
        viewModelScope.launch {
            repository.insertNote(note)
            _activeNote.value = null
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            if (_activeNote.value?.id == note.id) {
                _activeNote.value = null
            }
            repository.deleteNote(note)
        }
    }

    // Helper to decode todo items
    fun parseTodoItems(json: String): List<TodoItem> {
        return converters.fromString(json) ?: emptyList()
    }

    // Helper to encode todo items
    fun formatTodoItems(items: List<TodoItem>): String {
        return converters.fromList(items)
    }

    // Standard list of pastel backgrounds for light/dark themes
    val keepColors = listOf(
        NoteColor("#FFFFFF", "Default"), // Pure White / Default Dark Card is handled in theme or visually
        NoteColor("#F28B82", "Red"),
        NoteColor("#FBBC04", "Orange"),
        NoteColor("#FFF475", "Yellow"),
        NoteColor("#CCFF90", "Green"),
        NoteColor("#A7FFEB", "Teal"),
        NoteColor("#CBF0F8", "Blue"),
        NoteColor("#AECBFA", "Sky Blue"),
        NoteColor("#D7AEFB", "Purple"),
        NoteColor("#FDCFE8", "Pink"),
        NoteColor("#E6C9A8", "Sand"),
        NoteColor("#E8EAED", "Charcoal")
    )
}

data class NoteColor(
    val hexValue: String,
    val name: String
)

class NoteViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NoteViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
