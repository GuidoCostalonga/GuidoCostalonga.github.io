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

rootProject.name = "SportInTV"
// raccolta: i connettori alle fonti, in Kotlin puro. Li usa il servizio su
// GitHub Actions e li usa l'app quando il servizio non risponde.
include(":raccolta")
include(":app")
