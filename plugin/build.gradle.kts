plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.rxac"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")        // Paper API
    maven("https://repo.dmulloy2.net/repository/public/")            // ProtocolLib
    maven("https://repo.viaversion.com/")                            // ViaVersion (softdepend)
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.1.0")
    compileOnly("com.viaversion:viaversion-api:4.10.0")
    // Gson is bundled with Paper at runtime; compileOnly avoids shading it.
    compileOnly("com.google.code.gson:gson:2.10.1")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(17)
    }
    processResources {
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }
    shadowJar {
        archiveClassifier.set("")
        // Relocate any future bundled libs here to avoid conflicts.
    }
    build {
        dependsOn(shadowJar)
    }
}
