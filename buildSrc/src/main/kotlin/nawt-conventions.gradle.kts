plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(
        listOf(
            // Native backends use restricted FFM APIs by design; -restricted off.
            "-Xlint:all,-serial,-processing,-options,-restricted,-try",
            "-Werror"
        )
    )
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED"
    )
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED"
    )
}

dependencies {
    "testImplementation"(platform("org.junit:junit-bom:5.11.4"))
    "testImplementation"("org.junit.jupiter:junit-jupiter")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}
