rootProject.name = "swat"

include(":swat-api")
include(":swat-backend-macos")
include(":swat-backend-gtk")
include(":swat-samples")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}
