import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

android {
    namespace = "com.nexlock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nexlock"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "com.nexlock.HiltTestRunner"
        vectorDrawables { useSupportLibrary = true }
        // SHA-256 of the signing certificate, used for runtime integrity verification.
        // Replace with your own certificate digest before publishing a release build.
        buildConfigField("String", "EXPECTED_CERT_SHA256", "\"${project.findProperty("nexlock.certSha256") ?: ""}\"")
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE.md", "META-INF/LICENSE-notice.md")
    }
    bundle {
        language { enableSplit = true }
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":core-common"))
    implementation(project(":core-security"))
    implementation(project(":core-database"))
    implementation(project(":core-design"))
    implementation(project(":feature-dashboard"))
    implementation(project(":feature-app-lock"))
    implementation(project(":feature-authentication"))
    implementation(project(":feature-vault"))
    implementation(project(":feature-intruder-detection"))
    implementation(project(":feature-automation"))
    implementation(project(":feature-settings"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    kspAndroidTest(libs.hilt.compiler)
}
