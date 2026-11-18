pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "Studysh"
include(":app")
include(":speech-smoke")
includeBuild("piper-kotlin") {
    dependencySubstitution {
        substitute(module("dev.ihorshevchuk.piper:piper-player")).using(project(":piper-player"))
        substitute(module("dev.ihorshevchuk.piper:piper-engine")).using(project(":piper-engine"))
        substitute(module("dev.ihorshevchuk.piper:piper-utils")).using(project(":piper-utils"))
    }
}
