package com.example.sudokuocr.solver

object SudokuJni {
    init {
        System.loadLibrary("sudokuocr")
    }

    /**
     * Solves a sudoku puzzle.
     * @param grid Flat IntArray of 81 values, 0 = empty cell.
     * @return Solved flat IntArray(81), or null if no solution exists / invalid input.
     */
    external fun solve(grid: IntArray): IntArray?
}