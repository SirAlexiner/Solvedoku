plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.sudokuocr"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.sudokuocr"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // ABI filters — arm64-v8a covers all modern real devices
        // x86_64 covers the emulator
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        // CMake flags for the solver — this @Incubating warning is harmless,
        // the API works fine and is standard practice
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20 -O2"
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // This block stays at the android level (path + version) — do not move it
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true      // switch from viewBinding to Compose
    }

    // Required for PyTorch native .so files
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            // OpenCV and PyTorch both bundle libc++_shared.so — pick one copy across all ABIs
            pickFirsts += "lib/**/libc++_shared.so"
        }
    }
}

dependencies {
    // --- Compose BOM (manages all compose versions together) ---
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // --- Core ---
    implementation(libs.androidx.core.ktx)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")

    // Required for Theme.Material3.DynamicColors.DayNight in themes.xml
    implementation("com.google.android.material:material:1.12.0")

    // --- Navigation ---
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // --- Splash screen ---
    implementation("androidx.core:core-splashscreen:1.0.1")

    // --- CameraX ---
    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // --- OpenCV (Maven artifact — no manual SDK needed) ---
    implementation("org.opencv:opencv:4.9.0")

    // --- PyTorch Mobile Lite ---
    implementation("org.pytorch:pytorch_android_lite:2.1.0")

    // --- Room (History) ---
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // --- DataStore (Settings) ---
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // --- Coil (image loading in History screen) ---
    implementation("io.coil-kt:coil-compose:2.6.0")

    // --- ViewModel ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}