#include <jni.h>
#include <android/log.h>
#include "sudoku.cpp"   // or include sudoku.h and link sudoku.cpp

#define LOG_TAG "SudokuJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {

/**
 * Takes a flat int[81] grid (0 = empty), returns solved flat int[81].
 * Returns null if no solution exists.
 */
JNIEXPORT jintArray JNICALL
Java_com_example_sudokuocr_solver_SudokuJni_solve(
        JNIEnv *env,
        jobject /* this */,
        jintArray inputGrid) {

    jint *flat = env->GetIntArrayElements(inputGrid, nullptr);
    if (!flat) return nullptr;

    Sudoku sudoku;
    std::array<std::array<int, 9>, 9> grid{};

    for (int i = 0; i < 81; i++) {
        grid[i / 9][i % 9] = flat[i];
    }

    int result = sudoku.validateLoadHints(grid);
    env->ReleaseIntArrayElements(inputGrid, flat, JNI_ABORT);

    if (result == -1) {
        LOGD("Invalid puzzle configuration");
        return nullptr;
    }

    bool solved = sudoku.solve();
    if (!solved) {
        LOGD("No solution found");
        return nullptr;
    }

    jintArray output = env->NewIntArray(81);
    jint outFlat[81];
    for (int i = 0; i < 81; i++) {
        outFlat[i] = sudoku.grid[i / 9][i % 9];
    }
    env->SetIntArrayRegion(output, 0, 81, outFlat);
    return output;
}

} // extern "C"