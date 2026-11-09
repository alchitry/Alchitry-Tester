pluginManagement {
    repositories {
        mavenLocal()
        google()
        gradlePluginPortal()
        //maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io")
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

includeBuild("../Alchitry-Interface") {
    dependencySubstitution {
        substitute(module("com.alchitry:Alchitry-Interface")).using(project(":"))
    }
}

//sourceControl {
//    gitRepository(java.net.URI.create("https://github.com/alchitry/Alchitry-Interface.git")) {
//        producesModule("com.alchitry:Alchitry-Interface")
//    }
//}

rootProject.name = "Alchitry Tester"