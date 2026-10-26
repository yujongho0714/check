// 프로젝트 전체에서 쓰는 플러그인 버전 (여기서는 선언만, 실제 적용은 app/build.gradle.kts)
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
