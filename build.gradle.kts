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
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Modern libxposed API (NOT legacy de.robv.android.xposed:api).
    // compileOnly: the framework provides these classes at runtime.
    compileOnly("io.github.libxposed:api:102.0.0")
}
