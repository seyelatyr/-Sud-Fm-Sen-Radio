plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.newandromo.dev1660662.app2146388"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.newandromo.dev1660662.app2146388"
        minSdk = 26
        targetSdk = 36
        versionCode = 1046
        versionName = "2.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.ui:ui:1.11.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.11.4")
    implementation("androidx.compose.foundation:foundation:1.11.4")
    implementation("androidx.compose.material3:material3:1.4.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.11.4")

    implementation("androidx.media3:media3-common:1.10.1")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")

    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
}
