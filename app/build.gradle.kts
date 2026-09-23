plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mbk.hayplan"
    compileSdk = 37

    val automaticBuildProperty = providers.gradleProperty("hayPlanBuildNumber").orNull
    val automaticBuild = automaticBuildProperty?.toIntOrNull()
    val releaseKeystore = providers.environmentVariable("HAY_PLAN_DEBUG_KEYSTORE").orNull
    require(automaticBuildProperty == null || automaticBuild != null) {
        "hayPlanBuildNumber must be an integer."
    }
    require(automaticBuild == null || !releaseKeystore.isNullOrBlank()) {
        "HAY_PLAN_DEBUG_KEYSTORE is required for versioned release builds."
    }

    defaultConfig {
        applicationId = "com.mbk.hayplan"
        minSdk = 26
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = automaticBuild ?: 14
        versionName = automaticBuild?.let { "0.9.2.$it" } ?: "0.9.2"
    }

    // CI supplies the private update-compatible key; local unversioned builds use the normal debug key.
    signingConfigs.getByName("debug") {
        releaseKeystore?.takeUnless(String::isBlank)?.let { storeFile = file(it) }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Preserve the installed app's certificate while shipping a non-debuggable APK.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Both bundled languages must remain available to the app's persisted language override.
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    // ui-test-junit4 pulls Espresso 3.5, whose idle check crashes on Android 16 emulators.
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
