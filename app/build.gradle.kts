import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.util.Properties
import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val pinnedNdkVersion = "30.0.16248370"
val buildGitSha = System.getenv("EPICHUB_SOURCE_SHA")
    ?.takeIf { it.isNotBlank() }
    ?: System.getenv("GITHUB_SHA")?.takeIf { it.isNotBlank() }
    ?: "unknown"
val rustCrateDirectory = rootProject.layout.projectDirectory.dir("rust/engine")
val rustManifest = rustCrateDirectory.file("Cargo.toml")
val rustSources = rustCrateDirectory.dir("src")
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

abstract class CargoNdkBuildTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val manifestFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:Internal
    abstract val crateDirectory: DirectoryProperty

    @get:Internal
    abstract val androidSdkDirectory: DirectoryProperty

    @get:Input
    abstract val ndkVersion: Property<String>

    @get:Input
    abstract val releaseBuild: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun buildRustLibrary() {
        val ndkDirectory = androidSdkDirectory
            .dir("ndk/${ndkVersion.get()}")
            .get()
            .asFile
        require(ndkDirectory.isDirectory) {
            "Pinned Android NDK ${ndkVersion.get()} is not installed at ${ndkDirectory.absolutePath}."
        }

        val arguments = mutableListOf(
            "cargo",
            "ndk",
            "-t", "armeabi-v7a",
            "-t", "arm64-v8a",
            "-o", outputDirectory.get().asFile.absolutePath,
            "build",
            "--locked",
        )
        if (releaseBuild.get()) {
            arguments += "--release"
        }

        execOperations.exec {
            workingDir(crateDirectory.get().asFile)
            environment("ANDROID_NDK_HOME", ndkDirectory.absolutePath)
            environment("ANDROID_NDK_ROOT", ndkDirectory.absolutePath)
            commandLine(arguments)
        }
    }
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
        buildConfigField("String", "GIT_SHA", "\"$buildGitSha\"")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        create("performance") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            matchingFallbacks += listOf("release")
            versionNameSuffix = "-performance"
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
}

fun registerRustBuildTask(
    taskName: String,
    outputDirectory: DirectoryProperty,
    release: Boolean,
) = tasks.register<CargoNdkBuildTask>(taskName) {
    group = "rust"
    description = "Builds the EpicHub Rust engine for both production Android ARM ABIs."
    manifestFile.set(rustManifest)
    sourceDirectory.set(rustSources)
    crateDirectory.set(rustCrateDirectory)
    androidSdkDirectory.set(resolveAndroidSdkDirectory())
    ndkVersion.set(pinnedNdkVersion)
    releaseBuild.set(release)
    this.outputDirectory.set(outputDirectory)
}

val buildRustDebug = registerRustBuildTask(
    taskName = "buildRustDebug",
    outputDirectory = objects.directoryProperty().apply { set(rustDebugJniLibs) },
    release = false,
)

val buildRustRelease = registerRustBuildTask(
    taskName = "buildRustRelease",
    outputDirectory = objects.directoryProperty().apply { set(rustReleaseJniLibs) },
    release = true,
)

androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            buildRustDebug,
            CargoNdkBuildTask::outputDirectory,
        )
    }
    onVariants(selector().withBuildType("release")) { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            buildRustRelease,
            CargoNdkBuildTask::outputDirectory,
        )
    }
    onVariants(selector().withBuildType("performance")) { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            buildRustRelease,
            CargoNdkBuildTask::outputDirectory,
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.12.1")
    implementation("androidx.core:core:1.17.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
