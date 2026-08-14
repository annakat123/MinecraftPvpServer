plugins {
    java
}

group = "dev.pvpbot"
version = "1.0.11"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.citizensnpcs.co/repo")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("net.citizensnpcs:citizens-main:2.0.43-SNAPSHOT") {
        exclude(group = "*")
    }
    compileOnly("net.citizensnpcs:citizensapi:2.0.43-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.53.1.0") { exclude(group = "org.slf4j") }
    implementation("com.google.code.gson:gson:2.13.1")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:26.2.build.+")
    testImplementation("net.citizensnpcs:citizensapi:2.0.43-SNAPSHOT")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.test { useJUnitPlatform() }

tasks.register<JavaExec>("combatQa") {
    group = "verification"
    description = "Runs scripted combat QA, invariant negative controls and a bounded deterministic fuzz batch."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.pvpbot.qa.CombatQaMain"
    args("--mode=quick")
    project.findProperty("qaSeed")?.let { args("--seed=$it") }
    project.findProperty("qaScenario")?.let { args("--scenario=$it") }
    project.findProperty("qaSeeds")?.let { args("--generated=$it") }
}

tasks.register<JavaExec>("combatQaExtended") {
    group = "verification"
    description = "Runs the extended deterministic combat fuzz suite."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.pvpbot.qa.CombatQaMain"
    args("--mode=extended")
    project.findProperty("qaSeed")?.let { args("--seed=$it") }
    project.findProperty("qaScenario")?.let { args("--scenario=$it") }
    project.findProperty("qaSeeds")?.let { args("--generated=$it") }
}
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.jar {
    archiveBaseName = "PvPBot"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
}
