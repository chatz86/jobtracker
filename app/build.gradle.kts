plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.jobtracker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.jobtracker"
        minSdk = 30
        targetSdk = 37
        versionCode = 3
        versionName = "1.2"

    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.activity.ktx)
    implementation(libs.wear)
    implementation(libs.wear.ongoing)
    implementation(libs.datastore.preferences)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.play.services.wearable)
    implementation(libs.wear.watchface.complications.datasource)
    implementation(libs.wear.watchface.complications.data)
    implementation(libs.wear.tiles)
    implementation(libs.protolayout.material)
    implementation("com.google.guava:guava:33.1.0-android")
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}
