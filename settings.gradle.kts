pluginManagement {
    repositories {
        maven {
            name = "Fabric"
            // Kotlin DSL: the Java-style `url = "..."` setter is not available; `uri` is required.
            url = uri("https://maven.fabricmc.net/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "WorldTrace"