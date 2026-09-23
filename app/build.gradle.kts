import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val pinnedNdkVersion = "30.0.16248370"
val rustManifest = rootProject.file("rust/engine/Cargo.toml")
val rustDebugJniLibs = layout.buildDirectory.dir("generated/rustJniLibs/debug")
val rustReleaseJniLibs = layout.buildDirectory.dir("generated/rustJniLibs/release")

fun resolveAndroidSdkDirectory(): File {
    val environmentPath = System.getenv("ANDROID_SDK_ROOT")
        ?: System.getenv("ANDROID_HOME")
    if (!environmentPath.isNullOrBlank()) {
        return File(environmentPath)
    }

    val localPropertiesFile = rootProject.file("local.properties")
    require(localPropertiesFile.isFile) {
        "Android SDK not found. Set ANDROID_SDK_ROOT/ANDROID_HOME or sdk.dir in local.properties."
    }

    val properties = Properties()
    localPropertiesFile.inputStream().use(properties::load)
    val sdkPath = properties.getProperty("sdk.dir")
    require(!sdkPath.isNullOrBlank()) {
        "local.properties exists but does not define sdk.dir."
    }
    return File(sdkPath)
}

android {
    namespace = "com.epicefi.epichub"
    compileSdk = 37
    ndkVersion = pinnedNdkVersion

    defaultConfig {
        applicationId = "com.epicefi.epichub"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-dev"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("debug").jniLibs.srcDir(rustDebugJniLibs)
    sourceSets.getByName("release").jniLibs.srcDir(rustReleaseJniLibs)
}

fun registerRustBuildTask(
    taskName: String,
    outputDirectory: File,
    release: Boolean,
) = tasks.register<Exec>(taskName) {
    group = "rust"
    description = "Builds the EpicHub Rust engine for both production Android ARM ABIs."
    workingDir(rootProject.projectDir)
    inputs.file(rustManifest)
    inputs.dir(rootProject.file("rust/engine/src"))
    outputs.dir(outputDirectory)

    doFirst {
        val ndkDirectory = File(resolveAndroidSdkDirectory(), "ndk/$pinnedNdkVersion")
        require(ndkDirectory.isDirectory) {
            "Pinned Android NDK $pinnedNdkVersion is not installed at ${ndkDirectory.absolutePath}."
        }
        environment("ANDROID_NDK_HOME", ndkDirectory.absolutePath)
    }

    val arguments = mutableListOf(
        "cargo",
        "ndk",
        "-t", "armeabi-v7a",
        "-t", "arm64-v8a",
        "-o", outputDirectory.absolutePath,
        "build",
        "--manifest-path", rustManifest.absolutePath,
    )
    if (release) {
        arguments += "--release"
    }
    commandLine(arguments)
}

val buildRustDebug = registerRustBuildTask(
    taskName = "buildRustDebug",
    outputDirectory = rustDebugJniLibs.get().asFile,
    release = false,
)

val buildRustRelease = registerRustBuildTask(
    taskName = "buildRustRelease",
    outputDirectory = rustReleaseJniLibs.get().asFile,
    release = true,
)

tasks.configureEach {
    when (name) {
        "mergeDebugJniLibFolders" -> dependsOn(buildRustDebug)
        "mergeReleaseJniLibFolders" -> dependsOn(buildRustRelease)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
