plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // Removed Firebase plugin ID
}

android {
    namespace = "com.arua.lonyichat"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.arua.lonyichat"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    compileOptions  {
        // ✨ DEFINITIVE FIX: Using explicit assignment to properties of the CompileOptions object ✨
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-splashscreen:1.0.1")

    // --- UPDATED
    // FOR MODERN EMOJI/PLATFORM SUPPORT --- // 🐛 FIX: Ensure all parts of the comment block are correctly commented
    implementation("androidx.appcompat:appcompat:1.7.0") // BUMPED from 1.6.1 for emoji compatibility
    implementation("androidx.core:core-ktx:1.13.1") // BUMPED from 1.10.1 for latest features
    implementation("com.google.android.material:material:1.12.0") // BUMPED from 1.11.0 for emoji compatibility

    // ✨ FIX: Explicitly adding androidx.emoji2 to ensure correct backporting of modern emojis on older Android versions. ✨
    implementation("androidx.emoji2:emoji2:1.4.0")

    // Removed all Firebase and associated dependencies

    // OkHttp for network calls
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ✨ ADDED: Gson for parsing JSON from your backend ✨
    implementation("com.google.code.gson:gson:2.10.1")

    // ADDED: Coil for image loading from URLs
    implementation("io.coil-kt:coil-compose:2.6.0")

    // REMOVED: androidx.compose.material:material-swipe:1.3.0 - This dependency caused a resolution error. We will use the native Material3 approach instead.

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}