package com.example.sudokuocr.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class SolveRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val inputGrid: String,      // comma-separated int[81]
    val solvedGrid: String,     // comma-separated int[81]
    val imagePath: String?,     // MediaStore URI string, nullable
    val solveTimeMs: Long
)

// ── Grid serialization helpers ─────────────────────────────────────────────────

fun IntArray.toGridString(): String = joinToString(",")

fun String.toIntArray81(): IntArray {
    val parts = split(",")
    if (parts.size != 81) return IntArray(81)
    return IntArray(81) { i -> parts[i].trim().toIntOrNull() ?: 0 }
}