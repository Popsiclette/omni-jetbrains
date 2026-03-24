import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
  java
  id("org.jetbrains.intellij.platform") version "2.13.1"
  id("org.jetbrains.changelog") version "1.3.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("javaVersion").get().toInt()))
  }
}

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  intellijPlatform {
    intellijIdea(providers.gradleProperty("platformVersion"))
    bundledPlugins(
      providers.gradleProperty("platformBundledPlugins").map { line ->
        line.split(',').map { it.trim() }.filter { it.isNotEmpty() }
      },
    )
    plugins(
      providers.gradleProperty("platformPlugins").map { line ->
        line.split(',').map { it.trim() }.filter { it.isNotEmpty() }
      },
    )
    bundledModules(
      providers.gradleProperty("platformBundledModules").map { line ->
        line.split(',').map { it.trim() }.filter { it.isNotEmpty() }
      },
    )
  }
}

changelog {
  version.set(providers.gradleProperty("pluginVersion"))
  groups.set(emptyList())
}

intellijPlatform {
  buildSearchableOptions = false
  instrumentCode = false

  pluginConfiguration {
    id = providers.gradleProperty("pluginGroup")
    name = providers.gradleProperty("pluginName")
    version = providers.gradleProperty("pluginVersion")

    description =
      providers.fileContents(layout.projectDirectory.file("README.md")).asText.map { text ->
        val start = "<!-- Plugin description -->"
        val end = "<!-- Plugin description end -->"
        val lines = text.lines()
        if (!lines.containsAll(listOf(start, end))) {
          throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
        }
        lines.subList(lines.indexOf(start) + 1, lines.indexOf(end)).joinToString("\n").let(::markdownToHTML)
      }

    changeNotes = providers.provider { changelog.getUnreleased().toHTML() }

    ideaVersion {
      sinceBuild = providers.gradleProperty("pluginSinceBuild")
      untilBuild = providers.gradleProperty("pluginUntilBuild")
    }

    vendor {
      url = providers.gradleProperty("pluginVendorUrl")
    }
  }

  publishing {
    token = providers.environmentVariable("PUBLISH_TOKEN")
    channels =
      providers.gradleProperty("pluginVersion").map { v ->
        listOf(v.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
      }
  }

  pluginVerification {
    ides {
      providers
        .gradleProperty("pluginVerifierIdeVersions")
        .get()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { build -> create(IntelliJPlatformType.IntellijIdeaUltimate, build) }
    }
  }
}

tasks {
  wrapper {
    gradleVersion = providers.gradleProperty("gradleVersion").get()
  }

  publishPlugin {
    dependsOn("patchChangelog")
  }
}
