import java.util.Properties
import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.symbol.processing)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt.android)
}

// Function to extract client_id from the JSON file
fun fetchGoogleClientId(): String {
    val rootDir = project.rootDir
    val jsonFile = rootDir.listFiles()?.find { 
        it.name.startsWith("client_secret_") && it.name.endsWith(".json") 
    }
    
    return if (jsonFile != null && jsonFile.exists()) {
        try {
            val json = JsonSlurper().parseText(jsonFile.readText()) as Map<*, *>
            val installed = json["installed"] as? Map<*, *>
            installed?.get("client_id")?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    } else {
        ""
    }
}

val googleClientId = fetchGoogleClientId()

android {
    namespace = "com.example.mempass"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.mempass"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Pass Client ID to code safely from the JSON file
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleClientId\"")

        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    
    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // SQLCipher for Room Encryption
    implementation(libs.sqlcipher)
    implementation(libs.androidx.sqlite.ktx)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Security & Biometrics
    implementation(libs.androidx.biometric)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.security.crypto)

    // Google Drive & JSON
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.gson)

    // Crypto
    implementation(libs.argon2kt)
    implementation(libs.kotlin.onetimepassword)
    implementation(libs.zxcvbn)

    // CameraX (for Intruder Selfie)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit (for Document OCR)
    implementation(libs.play.services.mlkit.text.recognition)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Autofill Inline Suggestions
    implementation("androidx.autofill:autofill:1.1.0")

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
