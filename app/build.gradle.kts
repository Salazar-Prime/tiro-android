plugins {
    id("com.android.application")
}

val configuredAppVersionCode =
    providers.gradleProperty("appVersionCode").map(String::toInt).orElse(1)
val configuredAppVersionName =
    providers.gradleProperty("appVersionName").orElse("0.1.0-dev")

android {
    namespace = "com.salazarprime.tiro"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.salazarprime.tiro"
        minSdk = 31
        targetSdk = 36
        versionCode = configuredAppVersionCode.get()
        versionName = configuredAppVersionName.get()

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    buildTypes {
        release {
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
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
