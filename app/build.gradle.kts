plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.proyectoarpdm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.proyectoarpdm"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false

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
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // ARCore
    implementation(libs.arcore)

    // OBJ Loader
    implementation(libs.obj)

    // Google Maps
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    // GPS / Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    testImplementation(libs.junit)

    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}