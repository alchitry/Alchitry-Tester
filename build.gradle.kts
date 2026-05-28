import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import sun.jvmstat.monitor.MonitoredVmUtil.commandLine
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.*

plugins {
    kotlin("jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("org.jetbrains.compose") version "1.11.0"
    id("dev.hydraulic.conveyor") version "2.0"
    id("at.stnwtr.gradle-secrets-plugin") version "1.0.1"
}

group = "com.alchitry"
version = "1.0.0"

repositories {
    google()
    mavenLocal()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("com.github.alchitry:Alchitry-Interface:62639d9c4c")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    implementation("org.jetbrains.compose.material3:material3-desktop:1.9.0")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
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

fun TaskContainer.registerConveyorTask(name: String, conveyorCommand: String = name, arg: String? = null) {
    register<Exec>(name) {
        description = "Conveyor task"
        group = "conveyor"
        dependsOn("jar")
        executable = "/home/justin/.npm-global/bin/conveyor"
        val rootKey = secrets.get("conveyorRootKey")
        if (arg == null) {
            args("--passphrase=$rootKey", "make", conveyorCommand)
        } else {
            args("--passphrase=$rootKey", arg, "make", conveyorCommand)
        }
    }
}

tasks.registerConveyorTask("raspberryPi", "linux-app", "-Kapp.machines=linux.aarch64.glibc")
tasks.registerConveyorTask("raspberryPiDeb", "linux-tarball", "-Kapp.machines=linux.aarch64.glibc")

tasks.register<Exec>("copyToPi") {

    description = "Copy the project to the Raspberry Pi over SSH"
    group = "deploy"
    dependsOn("raspberryPi")
    executable = "rsync"
    args("-avz", "output", "alchitry@192.168.1.177:~/")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}