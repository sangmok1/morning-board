import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "com.morningboard"
version = "1.0.0"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation("org.json:json:20240303")
}

kotlin {
    jvmToolchain(21)
}

compose.desktop {
    application {
        mainClass = "com.morningboard.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "MorningBoard"
            packageVersion = "1.0.0"
            vendor = "sangmok lee"
            description = "아침보드 — 날씨·버스 데스크 보드"
            // jlink 런타임에 한국어 날짜(jdk.localedata)와 HTTPS용 EC 암호(jdk.crypto.ec) 포함
            modules("jdk.localedata", "jdk.crypto.ec")

            macOS {
                bundleID = "com.morningboard.desktop"
                dockName = "아침보드"
                // Developer ID 서명 (하드닝 런타임 자동 적용) — 공증은 notarize.sh 에서 처리
                signing {
                    sign.set(true)
                    identity.set("Developer ID Application: sangmok lee (469F7JUL9Q)")
                }
            }
        }
    }
}
