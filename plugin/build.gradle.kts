plugins {
    java
}

group = "com.rxac"
version = "0.4.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")        // Paper API
    maven("https://repo.dmulloy2.net/repository/public/")            // ProtocolLib
    maven("https://repo.viaversion.com/")                            // ViaVersion (softdepend)
}

dependencies {
    // All provided at runtime by the server / ProtocolLib, so compileOnly:
    // nothing needs to be shaded and the output jar stays tiny.
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0")
    compileOnly("com.viaversion:viaversion-api:5.9.1")
    compileOnly("com.google.code.gson:gson:2.10.1")
}

java {
    // Compile for Java 17 bytecode using whatever JDK (>=17) is running Gradle.
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
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
    jar {
        archiveBaseName.set("RXAC")
    }
}
