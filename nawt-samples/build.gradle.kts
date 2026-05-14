plugins {
    id("nawt-conventions")
    application
}

dependencies {
    implementation(project(":nawt-api"))
    runtimeOnly(project(":nawt-backend-macos"))
    runtimeOnly(project(":nawt-backend-gtk"))
}

application {
    mainClass = "cc.nawt.samples.HelloWorld"
    mainModule = "cc.nawt.samples"
}

val isMacHost = System.getProperty("os.name").lowercase().contains("mac")

fun JavaExec.nawtJvmArgs() {
    jvmArgs(
        "--enable-native-access=cc.nawt.backend.macos,cc.nawt.backend.gtk,ALL-UNNAMED"
    )
    if (isMacHost) {
        // NSApp must run on thread 0 on macOS.
        jvmArgs("-XstartOnFirstThread")
    }
    // Forward selected -D properties from the Gradle invocation into the forked JVM.
    System.getProperty("nawt.backend")?.let { systemProperty("nawt.backend", it) }
}

tasks.named<JavaExec>("run") {
    nawtJvmArgs()
}

tasks.register<JavaExec>("smoke") {
    group = "verification"
    description = "Run the non-interactive SmokeTest — opens a window, exercises setters, quits."
    classpath = sourceSets["main"].runtimeClasspath
    mainModule = "cc.nawt.samples"
    mainClass = "cc.nawt.samples.SmokeTest"
    nawtJvmArgs()
}

tasks.register<JavaExec>("demo") {
    group = "application"
    description = "Run the interactive Demo — menu bar, list view, dialogs."
    classpath = sourceSets["main"].runtimeClasspath
    mainModule = "cc.nawt.samples"
    mainClass = "cc.nawt.samples.Demo"
    nawtJvmArgs()
}

tasks.register<JavaExec>("tier1") {
    group = "application"
    description = "Run Tier1Demo — exercises every Tier 1 widget."
    classpath = sourceSets["main"].runtimeClasspath
    mainModule = "cc.nawt.samples"
    mainClass = "cc.nawt.samples.Tier1Demo"
    nawtJvmArgs()
}

tasks.register<JavaExec>("calculator") {
    group = "application"
    description = "Run the Calculator sample — four-function calculator on a Grid."
    classpath = sourceSets["main"].runtimeClasspath
    mainModule = "cc.nawt.samples"
    mainClass = "cc.nawt.samples.Calculator"
    nawtJvmArgs()
}

tasks.register<JavaExec>("landmarks") {
    group = "application"
    description = "Run the Landmarks sample — Apple's macOS Landmarks tutorial ported to NAWT."
    // Resources live in build/resources/main while classes (incl. module-info)
    // live in build/classes/java/main; with mainModule set, Gradle would put
    // them on different paths and the module's getResourceAsStream couldn't
    // see the resources. Routing through the JAR collapses both into one
    // module-path entry so classloader-based resource lookup works.
    dependsOn(tasks.named("jar"))
    classpath = files(tasks.named<Jar>("jar").get().archiveFile)
        .plus(configurations.runtimeClasspath.get())
    mainModule = "cc.nawt.samples"
    mainClass = "cc.nawt.samples.landmarks.Landmarks"
    nawtJvmArgs()
}

tasks.register<JavaExec>("mapSanity") {
    group = "verification"
    description = "Open an interactive MapKit map centred on Yosemite — sanity check for the Map widget."
    classpath = sourceSets["main"].runtimeClasspath
    mainModule = "cc.nawt.samples"
    mainClass = "cc.nawt.samples.MapSanity"
    nawtJvmArgs()
}

tasks.register<JavaExec>("tier1Smoke") {
    group = "verification"
    description = "Non-interactive Tier 1 smoke test — constructs every widget, exercises setters, quits."
    classpath = sourceSets["main"].runtimeClasspath
    mainModule = "cc.nawt.samples"
    mainClass = "cc.nawt.samples.Tier1Smoke"
    nawtJvmArgs()
}
