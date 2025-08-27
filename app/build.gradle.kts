plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.niq.niqpurchasecollector"
    compileSdk = 34
    signingConfigs {
        create("release") {
            storeFile = file("C:\\Users\\USER\\AndroidStudioProjects\\NIQPurchaseCollector\\key\\niqpurchasecollector.jks")
            storePassword = "Darius96005744"
            keyAlias = "key0"
            keyPassword = "Darius96005744"
        }
    }
    defaultConfig {
        applicationId = "com.niq.niqpurchasecollector"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "FTP_HOST", "\"${project.findProperty("FTP_HOST") ?: ""}\"")
        buildConfigField("int", "FTP_PORT", "${project.findProperty("FTP_PORT") ?: 0}")
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
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
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
    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-appcheck-playintegrity:17.3.0")
    implementation("com.google.firebase:firebase-appcheck-debug:17.3.0")
    implementation("com.google.firebase:firebase-storage:20.3.0")
}