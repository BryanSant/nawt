plugins {
    id("swat-conventions")
    application
}

dependencies {
    implementation(project(":swat-api"))
    runtimeOnly(project(":swat-backend-macos"))
    runtimeOnly(project(":swat-backend-gtk"))
}

application {
    mainClass = "io.github.swat.samples.HelloWorld"
    mainModule = "io.github.swat.samples"
}

val isMacHost = System.getProperty("os.name").lowercase().contains("mac")

fun JavaExec.swatJvmArgs() {
    jvmArgs(
        "--enable-native-access=io.github.swat.backend.macos,io.github.swat.backend.gtk,ALL-UNNAMED"
    )
    if (isMacHost) {
        // NSApp must run on thread 0 on macOS.
        jvmArgs("-XstartOnFirstThread")
    }
    // Forward selected -D properties from the Gradle invocation into the forked JVM.
    System.getProperty("swat.backend")?.let { systemProperty("swat.backend", it) }
}

tasks.named<JavaExec>("run") {
    swatJvmArgs()
}

tasks.register<JavaExec>("smoke") {
    group = "verification"
    description = "Run the non-interactive SmokeTest — opens a window, exercises setters, quits."
    classpath = sourceSets["main"].runtimeClasspath
    mainModule = "io.github.swat.samples"
    mainClass = "io.github.swat.samples.SmokeTest"
    swatJvmArgs()
}

tasks.register<JavaExec>("demo") {
    group = "application"
    description = "Run the interactive Demo — menu bar, list view, dialogs."
    classpath = sourceSets["main"].runtimeClasspath
    mainModule = "io.github.swat.samples"
    mainClass = "io.github.swat.samples.Demo"
    swatJvmArgs()
}

tasks.register<JavaExec>("tier1") {
    group = "application"
    description = "Run Tier1Demo — exercises every Tier 1 widget."
    classpath = sourceSets["main"].runtimeClasspath
    mainModule = "io.github.swat.samples"
    mainClass = "io.github.swat.samples.Tier1Demo"
    swatJvmArgs()
}

tasks.register<JavaExec>("calculator") {
    group = "application"
    description = "Run the Calculator sample — four-function calculator on a Grid."
    classpath = sourceSets["main"].runtimeClasspath
    mainModule = "io.github.swat.samples"
    mainClass = "io.github.swat.samples.Calculator"
    swatJvmArgs()
}

tasks.register<JavaExec>("tier1Smoke") {
    group = "verification"
    description = "Non-interactive Tier 1 smoke test — constructs every widget, exercises setters, quits."
    classpath = sourceSets["main"].runtimeClasspath
    mainModule = "io.github.swat.samples"
    mainClass = "io.github.swat.samples.Tier1Smoke"
    swatJvmArgs()
}
