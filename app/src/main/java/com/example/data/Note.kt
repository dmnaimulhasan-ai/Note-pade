package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

data class TodoItem(
    val id: String,
    val text: String,
    val isChecked: Boolean = false
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String,
    val isTodoList: Boolean = false,
    val todoItemsJson: String = "", // Serialized list of TodoItem
    val colorHex: String = "#FFFFFF", // Default white background
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val label: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
