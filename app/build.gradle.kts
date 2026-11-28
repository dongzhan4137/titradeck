import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningFile = rootProject.file(".signing/release.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
fun signingValue(name: String): String = requireNotNull(releaseSigning.getProperty(name)?.takeIf { it.isNotBlank() }) {
    "Missing $name in .signing/release.properties"
}

android {
    namespace = "com.titradeck.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.titradeck.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "1.0.4-alpha.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningFile.isFile) {
            create("release") {
                storeFile = releaseSigningFile.parentFile.resolve(signingValue("storeFile"))
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
                storeType = "PKCS12"
                enableV1Signing = false // minSdk 26 supports APK Signature Scheme v2.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningFile.isFile) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("ru.noties:jlatexmath-android:0.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.11.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
