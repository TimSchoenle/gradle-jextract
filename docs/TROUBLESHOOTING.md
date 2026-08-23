# Troubleshooting

The failures this plugin produces, what each one means, and what to change.

## `Version string '…' does not match expected pattern`

`toolVersion` has to spell an early-access build: a major version, `-jextract+`, a build number, and optionally `-` and a sub-build. `25-jextract+2-4` and `25-jextract+2` both pass; a bare `25` does not.

## `Failed to download jextract from …`

The URL in the message is built from the version and the detected platform. A 404 there means that combination was never published, most often because the version is right but the platform archive is not, or because the early-access build has been superseded and removed. [jdk.java.net/jextract](https://jdk.java.net/jextract/) lists the builds currently published.

## `Jextract binary 'jextract' not found in …`

The archive downloaded and extracted, but no `bin/jextract` turned up in the version directory or one level below it. Delete that directory under `<Gradle user home>/caches/jextract-tool` and let the next build fetch it again.

The same directory is the thing to delete after any download that failed partway. The plugin only writes its `.gradleJextractDownload` marker once the extraction finishes, so an interrupted download is re-fetched rather than trusted.

## `Unsupported OS/Arch combination: …`

The plugin maps `os.name` and `os.arch` onto the five archives jextract publishes. Anything else fails here rather than downloading an archive that will not run.

## `Only one library loading option can be configured`

A library declaration set both `libraryName` and `nativeLibraryLoading.resourcePath`. Pick one: `libraryName` for a library the machine already has, `nativeLibraryLoading` for one the JAR carries.

## `Header class not found: …`

The loader generation runs after jextract and expects the class it was told about. When `headerClassName` is set, jextract writes that class; when it is not, the plugin derives the name from the header file by replacing `.h` with `_h`. A `headerFile` whose name does not end in `.h` breaks that derivation, so set `headerClassName` explicitly.

## `Resource not found: …` at runtime

The loader looks for the resource path after both tokens are expanded and the platform's prefix and extension are applied. Check the JAR for the exact name in the message rather than for the template. [NATIVE_LIBRARY_LOADING.md](NATIVE_LIBRARY_LOADING.md) has the expansion rules.

## The IDE does not see the generated sources

The output directory is registered on the `main` source set when the `java` plugin is applied, but an IDE only learns about it when the project model is reloaded. Run the generation task, then reload the Gradle project.

## The task reruns when nothing changed

Its inputs are the header file's content, the target package, the header class name, the compiler arguments and the native-loading settings. A changed absolute path is not one of them; a changed compiler argument is. `--info` names the input that differed.
