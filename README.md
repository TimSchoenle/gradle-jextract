<!--
Generated from .github/templates/README.md.hbs — edit that file, not this one.

CI renders it on every pull request and commits the result back to the branch. A push to `main`
whose README.md does not match its template fails the `readme` job in
.github/workflows/update-files.yml, which is a required check.

The payload has two halves. The readme-variables action reads gradle.properties and walks docs/;
the plugin id and the default jextract build come from one command:

    ./gradlew -q readmeVariables

Nothing in this comment may contain a mustache that is not a real reference.
-->

# gradle-jextract

Gradle plugin that downloads jextract and generates Java FFM bindings, with optional bundled library loading.

[![Release](https://img.shields.io/github/v/release/TimSchoenle/gradle-jextract?sort=semver)](https://github.com/TimSchoenle/gradle-jextract/releases)
[![Build](https://img.shields.io/github/actions/workflow/status/TimSchoenle/gradle-jextract/build.yml?branch=main)](https://github.com/TimSchoenle/gradle-jextract/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/TimSchoenle/gradle-jextract)](LICENSE)
[![JDK](https://img.shields.io/badge/JDK-25-orange)](https://openjdk.org/projects/jdk/25/)

## What this is

[jextract](https://jdk.java.net/jextract/) turns a C header into Java bindings for the Foreign
Function & Memory API. It ships as a per-platform archive on `download.java.net` and is on no Maven
repository, so a build that wants it has to go and get it.

This plugin does that, then wires the result into the Java build. Each library declared in the
`jextract` block becomes a cacheable task, and its output directory joins the `main` source set, so
`compileJava` generates what it is about to compile.

It also writes the code that loads the native library at runtime, which jextract leaves to the
caller. That code can bind a library the machine already has, or one the JAR carries.

## Quick start

The plugin is published to Maven Central and not to the Gradle Plugin Portal, so the plugin
resolution has to be told where to look:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    java
    id("de.timscho.jextract") version "0.3.18"
}

jextract {
    libraries {
        create("mylib") {
            headerFile.set(file("src/main/c/mylib.h"))
            targetPackage.set("com.example.mylib")
        }
    }
}
```

```bash
./gradlew generateMylibBindings
```

The bindings land in `build/generated/sources/jextract/mylib` and compile with everything else.

## Table of contents

- [Features](#features)
- [Installation](#installation)
- [Usage](#usage)
- [Configuration](#configuration)
- [Compatibility](#compatibility)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [Security](#security)
- [License](#license)

## Features

- The tool downloads itself. A shared build service fetches the archive for the current platform on
  first use and unpacks it under the Gradle user home, so nothing has to be installed and nothing
  has to be on the `PATH`.
- One `create(name)` produces one `generate<Name>Bindings` task. A project binding six libraries
  gets six tasks and one download.
- **Tasks are cacheable.** Inputs are the header's content, the target package, the header class
  name and the compiler arguments, so a build that moves a header file does not regenerate.
- Native library loading is generated too, either as `-l` against a system library or as a loader
  class that extracts the right file out of the JAR for the running platform.
- The loader is injected into the class jextract produced, as a static initializer, by parsing that
  file rather than by appending text to it. Re-running the task over an existing tree does not stack
  up initializers.
- Five archives are resolved from `os.name` and `os.arch`: `linux-x64`, `linux-aarch64`,
  `macos-x64`, `macos-aarch64` and `windows-x64`. Anything else fails with the pair it detected.

## Installation

Coordinates `de.timscho:gradle-jextract`, version `0.3.18`, plugin id `de.timscho.jextract`. Add
`mavenCentral()` to `pluginManagement.repositories` in `settings.gradle.kts`, then:

```kotlin
plugins {
    id("de.timscho.jextract") version "0.3.18"
}
```

The legacy form puts the plugin on the buildscript classpath and needs no `pluginManagement` block:

```kotlin
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("de.timscho:gradle-jextract:0.3.18")
    }
}

apply(plugin = "de.timscho.jextract")
```

## Usage

A library declaration needs a header and a package. Everything else has a default:

```kotlin
jextract {
    libraries {
        create("opengl") {
            headerFile.set(file("src/main/c/gl.h"))
            targetPackage.set("com.example.gl")
            libraryName.set("GL")
        }

        create("audio") {
            headerFile.set(file("src/main/c/audio.h"))
            targetPackage.set("com.example.audio")
            headerClassName.set("AudioBindings")
            compilerArgs.set(listOf("-I", "/usr/local/include", "--include-function", "play"))
        }
    }
}
```

That declares `generateOpenglBindings` and `generateAudioBindings`, both in the `jextract` task
group. Neither needs to be invoked by hand once the `java` plugin is applied:

```bash
./gradlew build
```

`compileJava` depends on the generated directories, so building the project generates the bindings
first. Invoke a task directly when only one library changed.

## Configuration

`toolVersion` picks the jextract build, for the whole project rather than per library. It defaults
to `25-jextract+2-4`, the build this release was compiled against:

```kotlin
jextract {
    toolVersion.set("25-jextract+2-4")
}
```

The version string is matched against `<major>-jextract+<build>` with an optional `-<sub>`, because
the download URL is assembled out of those parts. A version that does not parse fails before
anything is fetched.

Each library takes `headerFile`, `targetPackage`, `headerClassName`, `compilerArgs`, and one of two
loading options: `libraryName` for a library the machine already has, or a `nativeLibraryLoading`
block for one the JAR carries. Setting both fails the task rather than picking one.
[docs/CONFIGURATION.md](docs/CONFIGURATION.md) has every property, its default and its effect on the
jextract command line.

## Compatibility

| | Supported |
| --- | --- |
| JDK | 25 |
| Gradle | 9.0 and later |
| jextract | `25-jextract+2-4` by default, any published early-access build |
| Platforms | `linux-x64`, `linux-aarch64`, `macos-x64`, `macos-aarch64`, `windows-x64` |

The JDK floor is the FFM API's. Bindings jextract writes do not compile below it.

## Documentation

| Document | Purpose |
| --- | --- |
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | Every property of the jextract extension, the task each library declaration creates, and how the jextract tool itself is fetched and cached. |
| [docs/NATIVE_LIBRARY_LOADING.md](docs/NATIVE_LIBRARY_LOADING.md) | The three ways to load the native library behind the bindings, and what the plugin generates for each. |
| [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md) | The failures this plugin produces, what each one means, and what to change. |

## Contributing

Issues and pull requests are welcome at [TimSchoenle/gradle-jextract](https://github.com/TimSchoenle/gradle-jextract). Commits follow
Conventional Commits, because release-please reads them to decide the next version and to write the
changelog.

```bash
./gradlew rewriteAndFormat
./gradlew build
```

The first applies OpenRewrite and Spotless, the second runs Checkstyle, the unit tests and the
functional tests. `README.md` is generated from `.github/templates/README.md.hbs`; a pull request
that edits the output has the edit overwritten by CI.

Both need a Code Genome Project download token, because OpenRewrite publishes there instead of Maven
Central and the build cannot configure without one. [Sign in for a
token](https://codegenomeproject.org/token), then put it in `~/.gradle/gradle.properties` — never in
a file under source control:

```properties
codegenomeUsername=you@example.com
codegenomePassword=cgp_...
```

## Security

Do not open a public issue for a vulnerability. [SECURITY.md](SECURITY.md) has the reporting
instructions and the supported versions.

## License

`Apache-2.0`. [LICENSE](LICENSE) has the terms.
