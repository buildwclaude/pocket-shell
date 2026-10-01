plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.pocketshell"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pocketshell"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        // Only the CPU types we build bash for (scripts/build-bash.sh):
        // arm64-v8a = phones, x86_64 = the CI emulator.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    // Extract native libs to disk on install: libbash.so has to exist as a real file in
    // the native-library folder to be executed (it's a program, not a loaded library).
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.termux.terminal.view)
}
