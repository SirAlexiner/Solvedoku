package com.example.sudokuocr.data.history

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<SolveRecord>>

    @Insert
    suspend fun insert(record: SolveRecord): Long

    @Delete
    suspend fun delete(record: SolveRecord)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}