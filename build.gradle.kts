import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.panoramicaod"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.panoramicaod"
        minSdk = 30
        targetSdk = 35
        versionCode = 3
        versionName = "1.2-release"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            // Values come from environment variables set in GitHub Actions.
            // Fallbacks allow local builds with the in-repo debug keystore.
            val ksFile = System.getenv("RELEASE_KEYSTORE") ?: "keystore/panoramic.jks"
            val ksPass = System.getenv("RELEASE_STORE_PASSWORD") ?: "panoramic123"
            val keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "panoramic"
            val keyPass = System.getenv("RELEASE_KEY_PASSWORD") ?: "panoramic123"
            storeFile = file(ksFile)
            storePassword = ksPass
            this.keyAlias = keyAlias
            keyPassword = keyPass
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // debug stays signed with the default debug key
        }
    }
}

dependencies {
    // Modern libxposed API (NOT legacy de.robv.android.xposed:api).
    compileOnly("io.github.libxposed:api:102.0.0")
}
