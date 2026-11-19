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
        // cs-design (com.chocosawse:design) is a private GitHub Packages repo: put gpr.user and gpr.key
        // (a token with read:packages) in ~/.gradle/gradle.properties. Never commit them.
        maven {
            name = "csDesign"
            url = uri("https://maven.pkg.github.com/Chocosawse/cs-design")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull
                password = providers.gradleProperty("gpr.key").orNull
            }
            content { includeGroup("com.chocosawse") }
        }
    }
}

rootProject.name = "OhMy"
include(":app")
