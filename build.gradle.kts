plugins {
    java
}

group = "net.civpvp"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.zaxxer:HikariCP:5.0.1")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }

    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // Storage tests run only when CIVCOSMETICS_TEST_DB_HOST points at a disposable MariaDB/MySQL database.
    testImplementation("com.zaxxer:HikariCP:5.0.1")
    testRuntimeOnly("org.mariadb.jdbc:mariadb-java-client:3.5.6")
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
    environment("CIVCOSMETICS_TEST_DB_HOST", System.getenv("CIVCOSMETICS_TEST_DB_HOST") ?: "")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
