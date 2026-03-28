package com.example.sudokuocr.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sudokuocr.data.history.SolveRecord
import com.example.sudokuocr.data.history.SudokuDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ── ViewModel ─────────────────────────────────────────────────────────────────

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = SudokuDatabase.getInstance(application).historyDao()

    val history = dao.getAllFlow().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    fun delete(record: SolveRecord) {
        viewModelScope.launch { dao.delete(record) }
    }

    fun clearAll() {
        viewModelScope.launch { dao.clearAll() }
    }
}