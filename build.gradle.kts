import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

plugins {
  java
  alias(libs.plugins.kotlin)
  alias(libs.plugins.intelliJPlatform)
  alias(libs.plugins.changelog)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
  jvmToolchain(providers.gradleProperty("javaVersion").get().toInt())
}

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

fun commaListProperty(name: String) =
  providers.gradleProperty(name).map { line ->
    line.split(',').map { it.trim() }.filter { it.isNotEmpty() }
  }

dependencies {
  intellijPlatform {
    intellijIdea(providers.gradleProperty("platformVersion"))
    bundledPlugins(commaListProperty("platformBundledPlugins"))
    plugins(commaListProperty("platformPlugins"))
    bundledModules(commaListProperty("platformBundledModules"))
  }
}

changelog {
  groups.empty()
  repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
  versionPrefix = ""
  version.set(providers.gradleProperty("pluginVersion"))
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

    val changelog = project.changelog
    changeNotes =
      providers.gradleProperty("pluginVersion").map { pluginVersion ->
        with(changelog) {
          renderItem(
            (getOrNull(pluginVersion) ?: getUnreleased()).withHeader(false).withEmptySections(false),
            Changelog.OutputType.HTML,
          )
        }
      }

    ideaVersion {
      sinceBuild = providers.gradleProperty("pluginSinceBuild")
    }

    vendor {
      url = providers.gradleProperty("pluginVendorUrl")
    }
  }

  signing {
    certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
    privateKey = providers.environmentVariable("PRIVATE_KEY")
    password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
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
      recommended()
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
