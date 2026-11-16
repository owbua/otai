plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.owbua.otai"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.owbua.otai"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "1.7.0"
    }

    // Release-Signatur kommt aus Umgebungsvariablen (in GitHub Actions aus den Repository-Secrets).
    // Der Schlüssel liegt nie im Repo. Ohne Variablen wird mit dem lokalen Debug-Schlüssel signiert –
    // so lässt sich ein Release-Build auch ohne Zugang zum echten Schlüssel ausprobieren.
    val releaseKeystore = System.getenv("OTAI_KEYSTORE")?.let { file(it) }?.takeIf { it.isFile }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("OTAI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("OTAI_KEY_ALIAS") ?: "otai"
                keyPassword = System.getenv("OTAI_KEY_PASSWORD") ?: System.getenv("OTAI_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("roborazzi.test.record", "true")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.maxHeapSize = "3g"
            }
        }
    }
}

/**
 * Der KI-Prompt wird nur in rezept-format/claude-prompt.md gepflegt und beim Build als Asset
 * in die App übernommen (Screen „KI-Prompt kopieren“).
 */
abstract class CopyKiPromptTask : DefaultTask() {
    @get:InputFile abstract val source: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile.apply { mkdirs() }
        source.get().asFile.copyTo(File(out, "claude-prompt.md"), overwrite = true)
    }
}

val copyKiPrompt = tasks.register<CopyKiPromptTask>("copyKiPrompt") {
    source.set(rootProject.layout.projectDirectory.file("rezept-format/claude-prompt.md"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyKiPrompt, CopyKiPromptTask::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
