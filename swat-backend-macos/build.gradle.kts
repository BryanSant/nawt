plugins {
    id("swat-conventions")
}

dependencies {
    api(project(":swat-api"))
}

val isMac = System.getProperty("os.name").lowercase().contains("mac")

tasks.withType<JavaCompile>().configureEach {
    onlyIf("only build macOS backend on macOS hosts") { isMac }
}

tasks.withType<Test>().configureEach {
    onlyIf("only test macOS backend on macOS hosts") { isMac }
}

tasks.named<Jar>("jar").configure {
    onlyIf("only jar macOS backend on macOS hosts") { isMac }
}
