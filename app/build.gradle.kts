plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.formastore.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.formastore.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 200
        versionName = "0.2.0"
    }
    val signingPath = System.getenv("FORMA_KEYSTORE_PATH")
    signingConfigs {
        if (!signingPath.isNullOrBlank()) create("forma") {
            storeFile = file(signingPath)
            storePassword = System.getenv("FORMA_STORE_PASSWORD")
            keyAlias = System.getenv("FORMA_KEY_ALIAS")
            keyPassword = System.getenv("FORMA_KEY_PASSWORD")
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release { isMinifyEnabled = false; if (!signingPath.isNullOrBlank()) signingConfig = signingConfigs.getByName("forma") }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    testImplementation("junit:junit:4.13.2")
}
