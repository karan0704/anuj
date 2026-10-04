pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Anuj"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:security")
include(":core:ui")
include(":core:backup")
include(":feature:task")
include(":feature:reminder")
include(":quality")
