plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services") // app/google-services.json 파일이 꼭 있어야 빌드됩니다
}

android {
    namespace = "com.jungdo.attend"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jungdo.attend" // Firebase 에 앱 등록할 때 이 이름 그대로 입력
        minSdk = 26        // 안드로이드 8.0 이상
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // 서명키: app/jungdo-sign.jks 가 있으면 항상 같은 키로 서명 → 새 APK 를 덮어 설치해도 설정 유지
    // (공개 저장소에는 올리지 말고 깃허브 Secrets 의 SIGNING_KEY_BASE64 로 넣기 — README 참고)
    val signFile = file("jungdo-sign.jks")
    if (signFile.exists()) {
        signingConfigs {
            create("jungdo") {
                storeFile = signFile
                storePassword = System.getenv("SIGN_PASS") ?: "jungdo1234"
                keyAlias = "jungdo"
                keyPassword = System.getenv("SIGN_PASS") ?: "jungdo1234"
            }
        }
    }
    buildTypes {
        debug {
            if (signFile.exists()) signingConfig = signingConfigs.getByName("jungdo")
        }
        release {
            isMinifyEnabled = false
            if (signFile.exists()) signingConfig = signingConfigs.getByName("jungdo")
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
    // 화면(Jetpack Compose)
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Firebase (실시간 DB + 로그인)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-auth")

    // 코루틴 (Firebase 작업을 await 로 기다리기)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
