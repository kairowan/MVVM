plugins {
    id("com.android.library")
    id("kotlin-android")
    kotlin("android")
}

android {
    compileSdk = 35
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        dataBinding = true
        buildConfig = true
    }
    namespace = "com.kt.ktmvvm.lib"

}

dependencies {

}