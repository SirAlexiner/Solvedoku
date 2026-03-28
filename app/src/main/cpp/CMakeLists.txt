cmake_minimum_required(VERSION 3.22.1)
project(sudokuocr)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(sudokuocr SHARED
        sudoku_jni.cpp
        sudoku.cpp
)

find_library(log-lib log)
target_link_libraries(sudokuocr ${log-lib} android)