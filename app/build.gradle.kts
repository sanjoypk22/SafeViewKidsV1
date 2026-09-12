plugins {
id("org.jetbrains.kotlin.android")
id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.application")
}

android {
    namespace = "com.safeviewkids.v1"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.safeviewkids.v1"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.8")
    implementation("androidx.compose.material3:material3:1.3.1")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.8")
}
