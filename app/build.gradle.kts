plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.jobtracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.jobtracker"
        minSdk = 30
        targetSdk = 37
        versionCode = 5
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.activity.ktx)
    implementation(libs.wear)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.play.services.wearable)
    implementation(libs.wear.tiles)
    implementation(libs.protolayout.material)
    implementation(libs.guava)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.coroutines.play.services)
    implementation(libs.wear.complications.data)
    implementation(libs.wear.complications.datasource)
    implementation(libs.wear.complications.datasource.ktx)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
