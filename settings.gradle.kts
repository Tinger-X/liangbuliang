pluginManagement {
  repositories {
    // 阿里云镜像置顶
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/google")
    maven("https://maven.aliyun.com/repository/gradle-plugin")

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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    // 阿里云镜像置顶
    maven("https://maven.aliyun.com/repository/public")
    maven("https://maven.aliyun.com/repository/google")

    google()
    mavenCentral()
  }
}

rootProject.name = "亮不亮"

include(":app")
