buildscript {
    dependencies {
        classpath (libs.kotlin.gradle.plugin)
        classpath (libs.plugin)
        classpath ("com.meituan.android.walle:plugin:1.1.7")
    }
}
@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
}
true