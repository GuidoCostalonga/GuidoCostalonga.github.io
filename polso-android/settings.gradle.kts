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

rootProject.name = "Polso"
// motore: modelli, analisi, importatori, esportazioni e cifratura in Kotlin
// puro, provati sulla JVM senza Android. app: interfaccia, archivio e fonti.
include(":motore")
include(":app")
