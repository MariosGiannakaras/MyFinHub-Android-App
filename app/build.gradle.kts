import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.CacheableTask
import java.net.URI
import java.security.MessageDigest
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.compose.screenshot")
    id("androidx.baselineprofile")
}

abstract class InjectBenchmarkHostManifestTask : DefaultTask() {
    @get:InputFile
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val updatedManifest: RegularFileProperty

    @TaskAction
    fun injectHost() {
        val input = mergedManifest.get().asFile.readText()
        val applicationClose = "</application>"
        require(applicationClose in input) { "Merged manifest has no <application> element." }

        var output = input
        if ("<profileable" in output) {
            output = output.replace(
                Regex("<profileable[^>]*/>"),
                "<profileable android:shell=\"true\" />",
            )
        }

        if ("BenchmarkProductActivity" !in output) {
            val profilingEntries = buildString {
                if ("<profileable" !in output) {
                    appendLine("        <profileable android:shell=\"true\" />")
                }
                appendLine("        <activity")
                appendLine("            android:name=\"app.myfinhub.android.BenchmarkProductActivity\"")
                appendLine("            android:exported=\"true\" />")
            }
            output = output.replace(applicationClose, "$profilingEntries    $applicationClose")
        }

        updatedManifest.get().asFile.apply {
            parentFile.mkdirs()
            writeText(output)
        }
    }
}

/**
 * Models are bundled into every APK/AAB, never downloaded at runtime. Pin the
 * upstream signed release commit AND Git blob identity/length, failing closed.
 */
@CacheableTask
abstract class StageReceiptModelsTask : DefaultTask() {
    @get:Input
    val upstreamCommit = "65727574dfcd264acbb0c3e07860e4e9e9b22185"

    @get:Input
    val modelPins = mapOf(
        "ell.traineddata" to "1419514:ed98ae1a88d84414da316e6eeab3232f2c68639b",
        "eng.traineddata" to "4113088:bbef4675053b5b468cdb477053e28b1c698ba08e",
    )

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun stage() {
        val directory = outputDirectory.get().asFile.resolve("receipt-tessdata")
        check(directory.isDirectory || directory.mkdirs())
        for ((name, pin) in modelPins) {
            val (size, expected) = pin.split(":")
            val length = size.toInt()
            fun verified(bytes: ByteArray): Boolean {
                if (bytes.size != length) return false
                val digest = MessageDigest.getInstance("SHA-1")
                digest.update("blob $size\u0000".toByteArray(Charsets.UTF_8))
                val hash = digest.digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
                return hash == expected
            }
            val target = directory.resolve(name)
            if (target.isFile && target.length() == length.toLong() && verified(target.readBytes())) continue
            val url = URI("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/$upstreamCommit/$name").toURL()
            val connection = url.openConnection() as javax.net.ssl.HttpsURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 45_000
            connection.instanceFollowRedirects = false
            val bytes = try {
                check(connection.responseCode == 200) { "Unable to stage pinned OCR model $name" }
                connection.inputStream.use { it.readNBytes(length + 1) }
            } finally {
                connection.disconnect()
            }
            check(verified(bytes)) { "Incorrect pinned OCR model bytes: $name" }
            target.writeBytes(bytes)
        }
    }
}

val stageReceiptModels = tasks.register<StageReceiptModelsTask>("stageReceiptModels") {
    outputDirectory.set(layout.buildDirectory.dir("generated/receipt-models"))
}

private fun String.asBuildConfigString(): String = "\"" +
    replace("\\", "\\\\").replace("\"", "\\\"") +
    "\""

// Public client configuration. These values identify public endpoints and are not server secrets.
// A developer/release environment can still override them with Gradle properties when necessary.
val myFinHubApiBaseUrl = providers.gradleProperty("MYFINHUB_API_BASE_URL")
    .orElse("https://mgfinhub.vercel.app")
val supabaseUrl = providers.gradleProperty("SUPABASE_URL")
    .orElse("https://ahsukppxwaiagampsuzb.supabase.co")
val supabasePublishableKey = providers.gradleProperty("SUPABASE_PUBLISHABLE_KEY")
    .orElse("sb_publishable_Ee7nzCpHN5AKwjXkPBvxdw_bTJXoJGC")
val androidUpdateChannel = providers.gradleProperty("ANDROID_UPDATE_CHANNEL")
    .orElse("production")

android {
    namespace = "app.myfinhub.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.myfinhub.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Use DSL runner arguments rather than CLI -P flags, preserving Gradle
        // configuration caching while sharding the hosted S24 test suite.
        val shardCountRaw = providers.environmentVariable("MYFINHUB_ANDROID_TEST_SHARD_COUNT").orNull
        val shardIndexRaw = providers.environmentVariable("MYFINHUB_ANDROID_TEST_SHARD_INDEX").orNull
        if (shardCountRaw != null || shardIndexRaw != null) {
            val count = shardCountRaw?.toIntOrNull()
            val index = shardIndexRaw?.toIntOrNull()
            require(count != null && count in 1..8 && index != null && index in 0 until count) {
                "Invalid Android instrumentation shard; both count and 0-based index are required."
            }
            testInstrumentationRunnerArguments["numShards"] = count.toString()
            testInstrumentationRunnerArguments["shardIndex"] = index.toString()
        }
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "MYFINHUB_API_BASE_URL", myFinHubApiBaseUrl.get().asBuildConfigString())
        buildConfigField("String", "SUPABASE_URL", supabaseUrl.get().asBuildConfigString())
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", supabasePublishableKey.get().asBuildConfigString())
        buildConfigField("String", "ANDROID_UPDATE_CHANNEL", androidUpdateChannel.get().asBuildConfigString())
        buildConfigField("boolean", "SELF_UPDATE_ENABLED", "true")
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }

        // Google Play testing/distribution must use Play's update path rather than MyFinHub's
        // direct PackageInstaller updater. This variant keeps the production application ID and
        // release optimizations while removing installer permissions in its manifest overlay.
        create("playRelease") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "SELF_UPDATE_ENABLED", "false")
        }

        // These names are owned by the Baseline Profile Gradle plugin for the release variant.
        // Only signing is customized. In particular, do not initWith(release): the plugin must
        // keep nonMinifiedRelease unminified/unshrunk so generated method signatures are valid.
        create("benchmarkRelease") {
            signingConfig = signingConfigs.getByName("debug")
        }
        create("nonMinifiedRelease") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    testOptions {
        animationsDisabled = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(stageReceiptModels, StageReceiptModelsTask::outputDirectory)
        if (variant.name == "benchmarkRelease" || variant.name == "nonMinifiedRelease") {
            val taskName = "inject" + variant.name.replaceFirstChar { it.uppercase() } + "BenchmarkHostManifest"
            val manifestUpdater = tasks.register<InjectBenchmarkHostManifestTask>(taskName)
            variant.artifacts
                .use(manifestUpdater)
                .wiredWithFiles(
                    InjectBenchmarkHostManifestTask::mergedManifest,
                    InjectBenchmarkHostManifestTask::updatedManifest,
                )
                .toTransform(SingleArtifact.MERGED_MANIFEST)
        }
    }
}

baselineProfile {
    // Keep the generated profile as reviewable source so release builds consume the exact
    // profile that passed CI/device generation rather than an opaque build-directory artifact.
    saveInSrc = true
    mergeIntoMain = true
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    val okhttpBom = platform("com.squareup.okhttp3:okhttp-bom:5.4.0")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    screenshotTestImplementation(composeBom)
    implementation(okhttpBom)
    testImplementation(okhttpBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    // Biometric 1.1.0 brings a pre-ActivityResult FragmentActivity transitively.
    // Pin a compatible stable Fragment: the old host rejects registry request codes > 16 bits.
    implementation("androidx.fragment:fragment:1.9.1")
    implementation("cz.adaptech.tesseract4android:tesseract4android:4.9.0")
    // Bundled, on-device receipt QR decoding and explicitly opened rear-camera preview.
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation3:navigation3-runtime:1.1.6")
    implementation("androidx.navigation3:navigation3-ui:1.1.6")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("com.squareup.okhttp3:okhttp")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    baselineProfile(project(":benchmark"))

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation("com.squareup.okhttp3:mockwebserver3")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4-accessibility")

    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha15")
    screenshotTestImplementation("androidx.compose.ui:ui-tooling")
}
