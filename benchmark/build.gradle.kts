plugins {
    id("com.android.test")
}

android {
    namespace = "com.epicefi.epichub.benchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        create("performance") {
            // The tested EpicHub performance APK is R8-shrunk, so AGP requires the
            // instrumentation APK to be shrunk as well. Keep the benchmark process
            // non-debuggable and apply the compatibility rules to both the ordinary
            // shrink configuration and the test-code shrink pass.
            isDebuggable = false
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            testProguardFiles("proguard-rules.pro")
            matchingFallbacks += listOf("performance")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.0")

    // The test APK is independently shrunk because the target performance APK is R8-shrunk.
    // Keep the benchmark process self-contained without changing EpicHub's measured runtime.
    implementation("androidx.arch.core:core-runtime:2.2.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.startup:startup-runtime:1.2.0")
    implementation("com.google.errorprone:error_prone_annotations:2.50.0")
}
