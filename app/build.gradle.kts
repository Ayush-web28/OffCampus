import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

// Where the deployed Cloudflare photo Worker lives (see worker/README.md). Override it for a local
// `wrangler dev` run by adding `photoWorkerUrl=http://localhost:8787` to local.properties (which is
// git-ignored) — everyone else just gets the committed default.
val photoWorkerUrl: String = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}.getProperty("photoWorkerUrl") ?: "https://offcampus-photos.offcampus.workers.dev"

android {
    namespace = "com.offcampus.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.offcampus.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "PHOTO_WORKER_URL", "\"$photoWorkerUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose UI, versioned together via the BOM so individual artifacts stay in sync.
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Firebase — BOM keeps Auth/Firestore versions compatible with each other.
    // Newer Firebase artifacts ship their Kotlin extensions built in, so no "-ktx" suffix needed.
    implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Loads profile photos from their Worker URL, with memory + disk caching built in.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Lets us call .await() on Firebase's Task objects instead of nesting listener callbacks.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
