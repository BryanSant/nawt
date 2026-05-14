rootProject.name = "nawt"

include(":nawt-api")
include(":nawt-backend-macos")
include(":nawt-backend-gtk")
include(":nawt-samples")

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
}
