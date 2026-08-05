// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Applied by :app only when a real google-services.json is present.
    alias(libs.plugins.google.services) apply false
}
