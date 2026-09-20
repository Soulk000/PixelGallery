plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
kotlin { jvmToolchain(17) }
android {
    namespace = "com.jinchenghao.pixelgallery"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jinchenghao.pixelgallery"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.1"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3:1.3.2")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")
    implementation("io.coil-kt.coil3:coil-gif:3.2.0")
}
