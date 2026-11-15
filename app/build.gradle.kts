plugins {
    id("com.google.devtools.ksp")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.thrive.pleos"
    compileSdk = 35
    useLibrary("android.car")

    defaultConfig {
        applicationId = "com.thrive.pleos"
        manifestPlaceholders["thriveAppLabel"] = "@string/app_name"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.thrive.pleos.driving.RecordingLifecycleChecks"
    }

    buildTypes {
        getByName("debug") {
            // Isolate emulator checks from the main app when another session is reviewing UI.
            providers.gradleProperty("thriveVerification").orNull?.let { verification ->
                applicationIdSuffix = if (verification == "figma") ".verification.figma" else ".verification"
                manifestPlaceholders["thriveAppLabel"] = if (verification == "figma") "Thrive 디자인 검증" else "Thrive 검증용"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    implementation("ai.pleos.playground:FusedLocation:1.1.3")
    implementation("ai.pleos.playground:TextToSpeech:3.3.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    testImplementation("junit:junit:4.13.2")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
