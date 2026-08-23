# Native library loading

The three ways to load the native library behind the bindings, and what the plugin generates for each.

jextract writes the bindings and leaves the loading to the caller. Two of the three options below close that gap for you.

## System libraries

`libraryName` names a library the machine already has:

```kotlin
create("opengl") {
    headerFile.set(file("src/main/c/gl.h"))
    targetPackage.set("com.example.gl")
    libraryName.set("GL")
}
```

The name reaches jextract as `-l GL` and is resolved at runtime through `System.mapLibraryName`, so the same declaration finds `libGL.so`, `libGL.dylib` or `GL.dll`. The library must be on the search path the platform uses: `LD_LIBRARY_PATH`, `DYLD_LIBRARY_PATH` or `PATH`.

Nothing is generated beyond the bindings themselves.

## Libraries bundled in the JAR

`nativeLibraryLoading` is for the case where the native code ships with the Java code:

```kotlin
create("mylib") {
    headerFile.set(file("src/main/c/mylib.h"))
    targetPackage.set("com.example.mylib")

    nativeLibraryLoading {
        resourcePath.set("native/{os.name}-{os.arch}/mylib")
        enableCaching.set(true)
    }
}
```

| Property | Type | Default | Purpose |
| --- | --- | --- | --- |
| `resourcePath` | `String` | none | Template for the resource holding the library, without the platform's prefix and extension. |
| `extractionDir` | `Directory` | `${java.io.tmpdir}/jextract-natives` | Where the extracted file is written. The absolute path is baked into the generated source. |
| `enableCaching` | `Boolean` | `false` | Hash the resource and look for an already-extracted copy before writing one. |

### How a resource path resolves

Two tokens are substituted at runtime, from `os.name` and `os.arch`:

| Token | Values |
| --- | --- |
| `{os.name}` | `windows`, `macos`, `linux` |
| `{os.arch}` | `amd64` for `amd64` or `x86_64`, `aarch64` for `aarch64` or `arm64` |

An architecture outside that table throws `UnsupportedOperationException` naming what it found.

The last segment is then decorated the way the platform spells a library: `mylib.dll` on Windows, `libmylib.dylib` on macOS, `libmylib.so` elsewhere. That is why `resourcePath` carries neither prefix nor extension. `native/{os.name}-{os.arch}/mylib` reads `/native/linux-amd64/libmylib.so` on Linux x86-64, so the resources are laid out like this:

```text
src/main/resources/
└── native/
    ├── linux-amd64/
    │   └── libmylib.so
    ├── windows-amd64/
    │   └── mylib.dll
    └── macos-aarch64/
        └── libmylib.dylib
```

### What gets generated

The task writes `<HeaderClass>_NativeLibraryLoader.java` into the target package and injects a static initializer into the header class jextract produced, so the library is loaded before any binding on that class runs. The initializer wraps the call and rethrows a checked failure as a `RuntimeException`, because a static initializer cannot declare one.

Injection is idempotent. The plugin parses the header class and skips it when an initializer already calls the loader, so re-running the task over an existing generated tree does not stack up initializers.

`load()` is `synchronized` and returns immediately after the first successful call. It expands the resource path, resolves the extraction directory, copies the resource out of the JAR and hands the absolute path to `System.load`.

With `enableCaching` set, one step is added before the extraction: the loader hashes the resource with SHA-256 and loads `<file>.<hash>` from the extraction directory when that file is already present. The extraction itself always writes `<file>`.

## Neither

A library declaration with no `libraryName` and no `resourcePath` generates bindings and nothing else. Load the library yourself before calling into them:

```java
static {
    System.load("/absolute/path/to/libmylib.so");
}
```
