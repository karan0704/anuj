import java.net.URI
import javax.inject.Inject

/**
 * The voice assistant: the offline speech engine, the speaker, the
 * flashlight, the service that listens for the assistant's name, the
 * assistant sheet, the microphone button on text fields and the voice
 * settings.
 *
 * What the assistant understands and does is in core:domain. This module
 * only connects those rules to the phone's microphone and speaker.
 */
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Fetches the English speech model and lays it out as an assets folder.
 *
 * The model is about 40 MB of binary files, so it is not kept in the
 * repository. It is downloaded the first time the module is built and kept
 * by Gradle after that; the built app carries it inside, so the phone never
 * needs the internet for speech.
 */
abstract class FetchSpeechModel : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    /** The folder the model sits in inside the app's assets. */
    @get:Input
    abstract val folderName: Property<String>

    @get:OutputDirectory
    abstract val assets: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun fetch() {
        val zip = temporaryDir.resolve("model.zip")
        URI(url.get()).toURL().openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
        val folder = folderName.get()
        files.sync {
            from(archives.zipTree(zip)) {
                /** The archive has one top folder named after the model's version; it is replaced by a fixed name. */
                eachFile { path = folder + "/" + path.substringAfter('/') }
                includeEmptyDirs = false
            }
            into(assets)
        }
    }
}

val fetchSpeechModel = tasks.register<FetchSpeechModel>("fetchSpeechModel") {
    url.set("https://alphacephei.com/vosk/models/vosk-model-small-en-us-${libs.versions.voskModel.get()}.zip")
    folderName.set("speech-model")
    assets.set(layout.buildDirectory.dir("speechModel"))
}

android {
    namespace = "com.karan.anuj.feature.voice"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(fetchSpeechModel, FetchSpeechModel::assets)
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.vosk.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core.ktx)
}
