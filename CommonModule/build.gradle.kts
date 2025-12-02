plugins {
    id ("com.android.library")
    id ("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ghn.commonmodule"
    compileSdk = 35
    kotlinOptions {
        jvmTarget = "1.8"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    api(project(":BaseModule"))
    api(project(":NetworkModule"))
    api(project(":EventModule"))
    api(project(":RouterModule"))
    api(project(":CapturePacketModule"))
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    testImplementation(libs.test.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)

    api(libs.lifecycle.viewmodel)
    api(libs.lifecycle.runtime)
    api(libs.androidx.appcompat)
    api(libs.kotlinx.core)
    api(libs.kotlinx.android)
    api(libs.androidx.core.ktx)
    api(libs.androidx.coordinatorlayout)
    api(libs.androidx.constraintlayout)
    api(libs.google.material)
    api(libs.okhttp.okhttp4.logging)
    api(libs.okhttp.okhttp4)
    api(libs.retrofit.retrofit2)
    api(libs.retrofit.retrofit2.gson)
    api(libs.retrofit.retrofit2.scalars)
    api(libs.rxjava.adapter)
    api(libs.rxlifecycle.rxlifecycle4.android)
    api(libs.rxlifecycle.rxlifecycle4.components)
    api(libs.github.glide)
    api(libs.androidx.room.ktx)
    api(libs.androidx.room.runtime)
    api(libs.jetbrains.annotations)
    api(libs.aliyun.httpdns)
    api(libs.jessyan.autosize)
    api(libs.dialog.avi.library)
    api(libs.dialog.blankj)
    api(libs.github.lqdbrv)
    api(libs.gencent.mmkv)
    api(libs.refresh.header.classics)
    api(libs.router)
    api(libs.multidex)
}