plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.room)
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

android {
    namespace = "dev.shadowcrawler.flashcards"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "dev.shadowcrawler.flashcards"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.quickie.bundled)
    implementation(libs.zxing.core)
    implementation(libs.litertlm.android)
    implementation(libs.litert)
//    implementation(libs.androidx.room.ktx)
//    ksp(libs.androidx.room.compiler)
}

// Forwards an optional local fixture path into unit tests' forked JVM — Gradle doesn't do this
// automatically for -D flags passed on the command line. Used by LayaTokenizerPortTest, which
// skips itself when this isn't set (the 34 MB tokenizer.json isn't checked into the repo).
tasks.withType<Test> {
    System.getProperty("laya.tokenizerJson")?.let { systemProperty("laya.tokenizerJson", it) }
}