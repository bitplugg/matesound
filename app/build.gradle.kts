import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.bitplugg.matesound"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.bitplugg.matesound"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.1"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        // Throwaway app signing key committed on purpose: every CI run must
        // produce APKs with the same signature, otherwise OTA updates can not
        // be installed over the previous version.
        create("matesound") {
            storeFile = rootProject.file("keystore/matesound.jks")
            storePassword = "matesound"
            keyAlias = "matesound"
            keyPassword = "matesound"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("matesound")
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.getByName("matesound")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.media3.common.util.UnstableApi",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.03.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:1.9.4")
    implementation("androidx.media3:media3-exoplayer-hls:1.9.4")
    implementation("androidx.media3:media3-session:1.9.4")
    implementation("androidx.media3:media3-datasource-okhttp:1.9.4")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
