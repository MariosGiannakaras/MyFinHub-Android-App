plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.android.compose.screenshot")
    id("androidx.baselineprofile")
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
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
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
        managedDevices {
            localDevices {
                create("compactApi36") {
                    device = "Pixel 6"
                    apiLevel = 36
                    systemImageSource = "aosp"
                    testedAbi = "x86_64"
                }
                create("foldableApi36") {
                    device = "Pixel Fold"
                    apiLevel = 36
                    systemImageSource = "aosp"
                    testedAbi = "x86_64"
                }
                create("expandedApi36") {
                    device = "Pixel Tablet"
                    apiLevel = 36
                    systemImageSource = "aosp"
                    testedAbi = "x86_64"
                }
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
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

    screenshotTestImplementation("com.android.tools.screenshot:screenshot-validation-api:0.0.1-alpha15")
    screenshotTestImplementation("androidx.compose.ui:ui-tooling")
}
