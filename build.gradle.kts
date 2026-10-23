plugins {
    // Latest release on maven.fabricmc.net as of 2026-10-21. Loom's 26.2 support is not confirmed by the Modrinth API (404).
    id("net.fabricmc.fabric-loom") version "1.19.2"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

repositories {
    maven("https://maven.fabricmc.net/")
}

dependencies {
    // Mojang names, no Yarn mappings. Unobfuscated 26.x builds need no mappings() line.
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    implementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// ./gradlew deploy: build the mod and put it in the Prism instance's mods folder. Restart Minecraft to load it.
val prismModsDir = file("C:/Users/wrong/AppData/Roaming/PrismLauncher/instances/Daedalus/minecraft/mods")

tasks.register<Sync>("deploy") {
    description = "Copies the built Daedalus jar into the Prism instance's mods folder."
    group = "daedalus"
    from(tasks.jar)
    into(prismModsDir)
    // Sync would delete the other mods, so only manage Daedalus jars.
    preserve {
        include("**")
        exclude("daedalus-client-*.jar")
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
