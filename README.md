# Solvedoku

## Introduction

[![License: CC BY-NC-ND 4.0](https://img.shields.io/badge/License-CC_BY--NC--ND_4.0-green.svg)](https://creativecommons.org/licenses/by-nc-nd/4.0/)

Solvedoku is an Android Sudoku solver that detects and overlays solutions onto puzzles in real time using your camera. Point the camera at any Sudoku puzzle and the solution is projected directly onto the grid. Gallery images are also supported.

## Authors
This project was developed by:
- Torgrim Thorsen [@SirAlexiner](https://github.com/SirAlexiner)

## Features

- Real-time Sudoku detection via camera
- AR perspective overlay — solution tracks the board as you move
- Gallery image solving
- Double-tap to freeze the camera feed
- Hold to temporarily hide the overlay
- Customisable solution and hint colours (full RGB picker)
- Dark, light, and follow-system themes
- Save solved images to gallery (AR composite or clean B/W)
- Solve history log
- First-time onboarding guide

## Tech Stack

### Application
- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Camera:** CameraX

### Computer Vision & AI
- **Computer Vision:** OpenCV 4.9
- **Digit Recognition:** PyTorch Mobile (TorchScript Lite)
- **Model:** Custom CNN trained on MNIST

### Solver
- Backtracking with forward checking
- Minimum Remaining Values (MRV) heuristic
- Most Constraining Value (MCV) heuristic
- Singleton arc-consistency propagation

### Data
- **Settings:** DataStore Preferences
- **History:** Room (SQLite)

## Getting Started

### Prerequisites

- Android Studio Meerkat or later
- Android SDK 26+
- NDK (for the native C++ solver)
- CMake 3.22+

### Build

1. Clone the repository:
    ```bash
    git clone https://github.com/yourusername/Solvedoku.git
    ```

2. Open the project in Android Studio.

3. Sync Gradle and let it resolve dependencies.

4. Connect a physical device (camera required) or use an emulator with a virtual camera.

5. Run the app:
    ```bash
    ./gradlew installDebug
    ```

### Model

The digit recognition model (`sudoku_ocr.ptl`) is a TorchScript Lite file and is **not included** in this repository. You must generate it yourself and place it at:

```
app/src/main/assets/sudoku_ocr.ptl
```

The training notebook and Linux-based solver code are available in a separate repository:

**[SudokuSolver — github.com/SirAlexiner/SudokuSolver](https://github.com/SirAlexiner/SudokuSolver)**

Follow the instructions there to train the model and export it to TorchScript Lite (`.ptl`) format, then copy the output file into the path above before building this project.

## Usage

| Action | Gesture |
|:-------|:--------|
| Solve puzzle | Point camera at a Sudoku grid |
| Hide overlay | Hold finger on screen |
| Freeze camera | Double-tap |
| New scan | Tap the refresh button |
| Solve from image | Tap the gallery button |
| Open menu | Tap **≡** (Solver screen only) |

## Project Structure

```
app/src/main/
├── java/com/example/sudokuocr/
│   ├── cv/                  # SudokuDetector, AROverlayGenerator
│   ├── data/
│   │   ├── history/         # Room database, DAO, SolveRecord
│   │   └── settings/        # AppSettings, SettingsRepository, DataStore
│   ├── history/             # HistoryScreen, HistoryViewModel
│   ├── ocr/                 # OcrModel (PyTorch Mobile)
│   ├── settings/            # SettingsScreen, SettingsViewModel
│   ├── solver/              # SolverScreen, SolverViewModel, SudokuJni
│   └── ui/theme/            # Theme, Color, Type
├── cpp/                     # sudoku.cpp, sudokuCVTorch.cpp, JNI bridge
└── assets/                  # sudoku_ocr.ptl
```

## Roadmap

- Difficulty estimation for detected puzzles
- Shareable solution cards
- Puzzle history thumbnails
- Tablet layout optimisation

## Contributing

Contributions to Solvedoku are welcome! To contribute, follow these steps:

- Fork the repository.
- Clone your forked repository to your local machine.
- Create a new branch for your changes.
- Make your changes and commit them with clear and concise messages.
- Push your changes to your forked repository.
- Submit a pull request to the original repository, explaining your changes and their significance.

Please adhere to the project's code of conduct and contribution guidelines provided in the [`./CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) and [`./CONTRIBUTING.md`](CONTRIBUTING.md) files, respectively.

## Support

For support, open an issue on GitHub or email `torgrilt@stud.ntnu.no`.

## License
This project is licensed under:
[![License: CC BY-NC-ND 4.0](https://img.shields.io/badge/License-CC_BY--NC--ND_4.0-green.svg)](https://creativecommons.org/licenses/by-nc-nd/4.0/)

You are free to download and use this code for educational, personal learning, or non-commercial purposes.
While we encourage these uses, please note that using this project for commercial distribution,
sales, or any form of monetization is not permitted.
