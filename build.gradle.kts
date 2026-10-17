plugins {
    id("fabric-loom") version "1.19.1"
    id("maven-publish")
}

val modVersion = project.property("mod_version") as String
val modId = project.property("mod_id") as String
val minecraftVersion = project.property("minecraft_version") as String
val loaderVersion = project.property("loader_version") as String
val yarnMappings = project.property("yarn_mappings") as String
val fabricVersion = project.property("fabric_version") as String
val junitVersion = project.property("junit_version") as String
// Named `modArchiveName` rather than `archiveBaseName`: Gradle exposes an `archiveBaseName`
// property on archive tasks, and a local val with the same name would shadow it inside a task
// configuration block, silently corrupting the LICENSE entry name.
val modArchiveName = project.property("archives_base_name") as String

version = modVersion
group = "dev.worldtrace"

base {
    archivesName = modArchiveName
}

repositories {
    // Used for JUnit, which is not part of the Minecraft dependency closure.
    mavenCentral()
}

// Development convenience: `./gradlew runClient -PwtQuickPlay=localhost:25565` launches the dev
// client straight into a server, which is handy for exercising the multiplayer world path without
// clicking through the menus. Unset by default.
val quickPlayTarget: String? = providers.gradleProperty("wtQuickPlay").orNull

loom {
    runs {
        named("client") {
            if (!quickPlayTarget.isNullOrBlank()) {
                programArgs("--quickPlayMultiplayer", quickPlayTarget)
            }
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Mod metadata templating. Kept in sync with fabric.mod.json and the README.
tasks.processResources {
    val properties = mapOf(
        "version" to modVersion,
        "minecraft_version" to minecraftVersion,
        "loader_version" to loaderVersion,
        "fabric_version" to fabricVersion
    )
    inputs.properties(properties)
    filteringCharset = "UTF-8"
    filesMatching("fabric.mod.json") {
        expand(properties)
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.jar {
    // Deterministic archives: no timestamps, stable entry order. Required for reproducible builds.
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from("LICENSE") {
        rename { fileName -> "LICENSE_$modArchiveName" }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = modArchiveName
            from(components["java"])
        }
    }
}