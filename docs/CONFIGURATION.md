# Configuration

Every property of the `jextract` extension, the task each library declaration creates, and how the jextract tool itself is fetched and cached.

## The extension

```kotlin
jextract {
    toolVersion.set("25-jextract+2-4")

    libraries {
        create("opengl") {
            headerFile.set(file("src/main/c/gl.h"))
            targetPackage.set("com.example.gl")
        }
    }
}
```

| Property | Type | Default | Purpose |
| --- | --- | --- | --- |
| `toolVersion` | `String` | the build the plugin was compiled against | Which jextract early-access build to download. Must match `<major>-jextract+<build>` with an optional `-<sub>` suffix. |
| `libraries` | container | empty | One entry per C library. The entry name becomes the task name and the output directory name. |

`toolVersion` is a single value for the whole project, not a per-library one. The download is a shared build service, so a build declaring six libraries downloads one tool.

## A library declaration

| Property | Type | Required | Purpose |
| --- | --- | --- | --- |
| `headerFile` | `RegularFile` | yes | The `.h` file jextract parses. Tracked by content, not by path, so moving the file does not invalidate the cache entry. |
| `targetPackage` | `String` | yes | Package the generated classes are written into. Becomes jextract's `--target-package`. |
| `headerClassName` | `String` | no | Name of the class jextract puts the top-level declarations on. Without it the header file name is used, with `.h` replaced by `_h`. |
| `compilerArgs` | `List<String>` | no | Arguments passed to jextract verbatim, after the generated ones and before the header path. |
| `libraryName` | `String` | no | A system library to bind against, passed as `-l`. Mutually exclusive with `nativeLibraryLoading`. |
| `nativeLibraryLoading` | block | no | Bundles the library in the JAR and generates a loader for it. See [NATIVE_LIBRARY_LOADING.md](NATIVE_LIBRARY_LOADING.md). |

Setting both `libraryName` and `nativeLibraryLoading.resourcePath` fails the task rather than picking one, because the two mean different things at runtime: one resolves through the system's library search path, the other extracts a file the JAR carries.

## Tasks

A library named `opengl` gets a task `generateOpenglBindings` in the `jextract` group, writing to `build/generated/sources/jextract/opengl`. When the `java` plugin is applied, that directory is added to the `main` source set, so `compileJava` depends on the generation.

The task is annotated `@CacheableTask`. Its inputs are the header file's content, the target package, the header class name, the compiler arguments and the native-loading settings; the tool version reaches it through the shared service.

## The tool cache

The plugin does not expect jextract on the `PATH`. A shared build service downloads it on first use from `download.java.net`, resolving the archive name from the version string and the current platform:

| Platform | Archive id |
| --- | --- |
| Windows x86-64 | `windows-x64` |
| Linux x86-64 | `linux-x64` |
| Linux AArch64 | `linux-aarch64` |
| macOS x86-64 | `macos-x64` |
| macOS AArch64 | `macos-aarch64` |

An OS or architecture outside that table fails the build with the pair it detected, rather than downloading an archive that cannot run.

The extracted tool lands in `<Gradle user home>/caches/jextract-tool/<version>`, with characters outside `[A-Za-z0-9.-]` replaced by `_` in the directory name. A `.gradleJextractDownload` marker file is written only after the extraction finishes. A directory without that marker is deleted and downloaded again, so an interrupted download does not leave a half-extracted tool that the next build treats as cached.

The binary is looked up at `bin/jextract`, or `bin/jextract.bat` on Windows, both directly under the version directory and one level below it, because some archives nest everything under a `jextract-<major>` folder.
