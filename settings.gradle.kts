// ============================================================================
//  settings.gradle.kts  —  quais repositorios e quais modulos existem no build
// ============================================================================
//  Para adicionar um novo modulo (ex.: uma lib de graficos), crie a pasta e
//  acrescente uma linha `include(":nome-do-modulo")` no fim deste arquivo.
// ============================================================================

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Proibe repositorios declarados dentro dos modulos: tudo vem daqui.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EasyPace"
include(":app")
