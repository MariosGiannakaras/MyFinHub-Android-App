import com.android.build.api.artifact.SingleArtifact
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
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

android {
    namespace = "app.myfinhub.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.myfinhub.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "MYFINHUB_API_BASE_URL", myFinHubApiBaseUrl.get().asBuildConfigString())
        buildConfigField("String", "SUPABASE_URL", supabaseUrl.get().asBuildConfigString())
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", supabasePublishableKey.get().asBuildConfigString())
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
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
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4-accessibility")

    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha15")
    screenshotTestImplementation("androidx.compose.ui:ui-tooling")
}
