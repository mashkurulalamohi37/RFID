import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release signing is read from keystore.properties (never committed). See README.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.rfid.rolltrack"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rfid.rolltrack"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "DEFAULT_BASE_URL", "\"http://118.179.223.40:7007/ords/\"")
            buildConfigField("boolean", "DEFAULT_DEMO_MODE", "false")
            buildConfigField("boolean", "ALLOW_DEMO_TOOLS", "true")
            buildConfigField("String", "DEVICE_MODEL", "\"CRUISE GeP\"")
            buildConfigField("String", "DEVICE_ID", "\"C16260702213\"")
            buildConfigField("String", "DEVICE_SERIAL", "\"547317bce70fa31c\"")
            buildConfigField("String", "HARDWARE_VERSION", "\"110\"")
            buildConfigField("String", "FUNCTION_MODEL", "\"2.0M8-16.0-4/64-X3PC(HD)GC13/5\"")
        }
        create("prod") {
            dimension = "env"
            buildConfigField("String", "DEFAULT_BASE_URL", "\"http://118.179.223.40:7007/ords/\"")
            buildConfigField("boolean", "DEFAULT_DEMO_MODE", "false")
            buildConfigField("boolean", "ALLOW_DEMO_TOOLS", "false")
            buildConfigField("String", "DEVICE_MODEL", "\"CRUISE GeP\"")
            buildConfigField("String", "DEVICE_ID", "\"C16260702213\"")
            buildConfigField("String", "DEVICE_SERIAL", "\"547317bce70fa31c\"")
            buildConfigField("String", "HARDWARE_VERSION", "\"110\"")
            buildConfigField("String", "FUNCTION_MODEL", "\"2.0M8-16.0-4/64-X3PC(HD)GC13/5\"")
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real key when keystore.properties exists; otherwise the local debug key so test
            // builds can be installed. Never ship a debug-signed APK to production devices.
            signingConfig = if (keystoreProps.isNotEmpty()) signingConfigs.getByName("release")
            else signingConfigs.getByName("debug")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
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
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}
