plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.niq.niqpurchasecollector"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.niq.niqpurchasecollector"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FTP_HOST", "\"${project.findProperty("FTP_HOST") ?: ""}\"")
        buildConfigField("String", "REMOTE_PATH", "\"${project.findProperty("REMOTE_PATH") ?: ""}\"")
        buildConfigField("String", "FTP_USER", "\"${project.findProperty("FTP_USER") ?: ""}\"")
        buildConfigField("String", "FTP_PASS", "\"${project.findProperty("FTP_PASS") ?: ""}\"")
        buildFeatures {
            buildConfig = true
        }
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.activity)
    implementation(libs.work.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(libs.apache.commons.net)
}