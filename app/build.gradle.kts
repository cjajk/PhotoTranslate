import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 固定 debug 签名：仓库只存 base64 文本，构建时还原 keystore（本地/CI 使用同一把，保证用户可直接覆盖安装）
val debugKeystoreFile = file("../keystore/debug.keystore")
val debugKeystoreB64 = file("../keystore/debug.keystore.b64")
if (!debugKeystoreFile.exists() && debugKeystoreB64.exists()) {
    debugKeystoreFile.parentFile?.mkdirs()
    debugKeystoreFile.writeBytes(Base64.getDecoder().decode(debugKeystoreB64.readText().trim()))
}

android {
    namespace = "com.cjajk.phototranslate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cjajk.phototranslate"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }

    // 固定 debug 签名：本地与云端构建使用同一把 keystore，保证用户可直接覆盖安装
    signingConfigs {
        getByName("debug") {
            storeFile = debugKeystoreFile
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
}
