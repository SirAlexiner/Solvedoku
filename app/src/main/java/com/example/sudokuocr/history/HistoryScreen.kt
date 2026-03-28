package com.example.sudokuocr.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sudokuocr.data.history.SolveRecord
import com.example.sudokuocr.data.history.toIntArray81
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: HistoryViewModel = viewModel()) {
    val history by vm.history.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear all")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No solves yet — point the camera at a sudoku!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                lazyItems(history, key = { it.id }) { record ->
                    HistoryCard(
                        record   = record,
                        onDelete = { vm.delete(record) }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all history?") },
            text  = { Text("This will permanently delete all ${history.size} saved solves.") },
            confirmButton = {
                TextButton(onClick = { vm.clearAll(); showClearDialog = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun HistoryCard(record: SolveRecord, onDelete: () -> Unit) {
    val formatter = remember { SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault()) }
    val given     = remember(record.inputGrid)  { record.inputGrid.toIntArray81() }
    val solved    = remember(record.solvedGrid) { record.solvedGrid.toIntArray81() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = formatter.format(Date(record.timestamp)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = "${record.solveTimeMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete",
                            tint   = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Mini sudoku grid
            MiniGrid(given = given, solved = solved)
        }
    }
}

@Composable
private fun MiniGrid(given: IntArray, solved: IntArray) {
    Column {
        for (row in 0..8) {
            MiniGridRow(row = row, given = given, solved = solved)
            if (row == 2 || row == 5) Spacer(Modifier.height(1.dp))
        }
    }
}

@Composable
private fun MiniGridRow(row: Int, given: IntArray, solved: IntArray) {
    Row {
        for (col in 0..8) {
            val index = row * 9 + col
            MiniGridCell(
                digit   = solved[index],
                isGiven = given[index] != 0
            )
            if (col == 2 || col == 5) Spacer(Modifier.width(1.dp))
        }
    }
}

@Composable
private fun MiniGridCell(digit: Int, isGiven: Boolean) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .padding(0.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = if (digit != 0) digit.toString() else "",
            fontSize   = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isGiven) FontWeight.Bold else FontWeight.Normal,
            color      = if (isGiven)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.primary
        )
    }
}