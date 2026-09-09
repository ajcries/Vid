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
        mavenCentral() // the yt-dlp library now lives here, not JitPack
    }
}
rootProject.name = "YtDlpCommanderPro"
include(":app")
