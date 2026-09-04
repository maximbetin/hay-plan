plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.mbk.hayplan"
    compileSdk = 37

    defaultConfig {
        val automaticBuild = providers.gradleProperty("hayPlanBuildNumber").orNull?.toIntOrNull()
        applicationId = "com.mbk.hayplan"
        minSdk = 26
        targetSdk = 37
        versionCode = automaticBuild ?: 11
        versionName = automaticBuild?.let { "0.7.0.$it" } ?: "0.7.0"
    }

    // CI supplies the restored test key explicitly; local builds keep their normal debug key.
    signingConfigs.getByName("debug") {
        System.getenv("HAY_PLAN_DEBUG_KEYSTORE")?.let { storeFile = file(it) }
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")

    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
