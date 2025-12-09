plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "anwar.mlsa.hadera.aou"
    compileSdk = 36

    defaultConfig {
        applicationId = "anwar.mlsa.hadera.aou"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // UI
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")

    // Network
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Image Loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Other dependencies
    implementation("com.github.yuriy-budiyev:code-scanner:2.3.2")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("io.github.everythingme:overscroll-decor-android:1.1.1")
    
    // WorkManager
    implementation("androidx.work:work-runtime:2.9.0")
}