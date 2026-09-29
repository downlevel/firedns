plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

// Version from the git tag in CI (v1.2.3 → 1.2.3), otherwise a development version.
val isTagBuild = System.getenv("GITHUB_REF_TYPE") == "tag"
val appVersionName = if (isTagBuild) System.getenv("GITHUB_REF_NAME").removePrefix("v") else "0.1.0-dev"
val appVersionCode = System.getenv("GITHUB_RUN_NUMBER")?.toInt() ?: 1

// Release signing only when a keystore is provided (CI: decoded from GitHub Secrets).
val releaseKeystore: String? = System.getenv("FIREDNS_KEYSTORE")

android {
    namespace = "dev.downlevel.firedns"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.downlevel.firedns"
        minSdk = 28
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        // Debug keystore committed to the repo (not a secret): every debug APK, CI included, has the
        // same signature, so a new APK installs over the previous one.
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                // trimEnd: a newline pasted by mistake into GitHub Secrets makes the key unreadable.
                storePassword = System.getenv("FIREDNS_KEYSTORE_PASSWORD")?.trimEnd('\n', '\r')
                keyAlias = System.getenv("FIREDNS_KEY_ALIAS")?.trim()
                keyPassword = System.getenv("FIREDNS_KEY_PASSWORD")?.trimEnd('\n', '\r')
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = true
    }
    testOptions {
        // android.util.Log & co. return default values in JVM tests instead of throwing.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
