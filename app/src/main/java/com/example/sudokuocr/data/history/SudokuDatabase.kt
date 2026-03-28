package com.example.sudokuocr.data.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SolveRecord::class], version = 1, exportSchema = false)
abstract class SudokuDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile private var INSTANCE: SudokuDatabase? = null

        fun getInstance(context: Context): SudokuDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SudokuDatabase::class.java,
                    "sudoku_db"
                ).build().also { INSTANCE = it }
            }
    }
}