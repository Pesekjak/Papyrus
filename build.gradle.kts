plugins {
    id("java")
    checkstyle
}

group = "me.pesekjak"
version = "1.0-BETA1"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://libraries.minecraft.net")
}

dependencies {
    implementation("io.papermc.paper:paper-api:1.20-R0.1-SNAPSHOT")
    implementation("io.papermc.paper:paper-mojangapi:1.20-R0.1-SNAPSHOT")
    implementation("com.mojang:brigadier:1.0.18")

    testImplementation(platform("org.junit:junit-bom:5.9.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

checkstyle {
    toolVersion = "10.3.1"
    configFile = File(rootDir, "code_style.xml")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(17)
    }
    test {
        useJUnitPlatform()
    }
}