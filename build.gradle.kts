plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.11.0"
    id("dev.hydraulic.conveyor") version "2.0"
    id("at.stnwtr.gradle-secrets-plugin") version "1.0.1"
}

group = "com.alchitry"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenLocal()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("com.github.alchitry:Alchitry-Interface:2c56e79acf")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.2")
    add("linuxAmd64", "org.jetbrains.compose.desktop:desktop-jvm-linux-x64:1.11.0")
    add("linuxAarch64", "org.jetbrains.compose.desktop:desktop-jvm-linux-arm64:1.11.0")
    add("macAmd64", "org.jetbrains.compose.desktop:desktop-jvm-macos-x64:1.11.0")
    add("macAarch64", "org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:1.11.0")
    add("windowsAmd64", "org.jetbrains.compose.desktop:desktop-jvm-windows-x64:1.11.0")
}



kotlin {
    jvmToolchain(23)
}

tasks.test {
    useJUnitPlatform()
}