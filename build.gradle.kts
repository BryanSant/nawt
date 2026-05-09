tasks.register("build") {
    dependsOn(subprojects.map { it.path + ":build" })
}
