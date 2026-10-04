pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.extendedclip.com/releases/")
    }
}

rootProject.name = "HCPlugins-TranslationKey"

val coreBuild = file(".hcplugins/HCPlugins-Core").takeIf { it.isDirectory }
    ?: file("../HCPlugins-Core")
require(coreBuild.resolve("settings.gradle.kts").isFile) {
    "Clone HCPlugins-Core next to this repository before building."
}
includeBuild(coreBuild) {
    dependencySubstitution {
        substitute(module("fr.noltox.hcplugins:core-api")).using(project(":core-api"))
    }
}

val placeholdersBuild = file(".hcplugins/HCPlugins-PlaceholdersExtra").takeIf { it.isDirectory }
    ?: file("../HCPlugins-PlaceholdersExtra")
require(placeholdersBuild.resolve("settings.gradle.kts").isFile) {
    "Clone HCPlugins-PlaceholdersExtra next to this repository before building."
}
includeBuild(placeholdersBuild) {
    dependencySubstitution {
        substitute(module("fr.noltox.hcplugins:placeholders-api")).using(project(":placeholders-api"))
    }
}

