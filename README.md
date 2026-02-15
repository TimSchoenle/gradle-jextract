# Gradle Jextract Plugin

A Gradle plugin that automates the download and execution of [jextract](https://jdk.java.net/jextract/) to generate Java Foreign Function & Memory (FFM) API bindings from C header files with bundled library loading support.

## Features

- **Automatic Jextract Download**: Downloads and caches the jextract tool automatically
- **Version Management**: Configure which jextract version to use
- **Multiple Libraries**: Generate bindings for multiple C libraries in a single project
- **Native Library Loading**: Built-in support for loading system libraries or bundling libraries in JARs
- **Incremental Builds**: Smart up-to-date checking for fast rebuilds
- **Automatic Integration**: Generated sources are automatically added to the main source set
- **Build Cache Support**: Fully cacheable tasks for efficient CI/CD pipelines
- **Configurable**: Flexible configuration options including custom header class names and compiler arguments

## Requirements

- Gradle 9.0 or higher
- Java 25 or higher (for FFM API support)

## Installation

Add the plugin to your `build.gradle.kts`:

```kotlin
plugins {
    id("de.timscho.jextract") version "0.2.1"
}
```

Or using the legacy plugin application:

```kotlin
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("de.timscho.jextract:0.2.1")
    }
}

apply(plugin = "de.timscho.jextract")
```

## Basic Usage

### Single Library

Generate bindings for a single C library:

```kotlin
jextract {
    toolVersion.set("25-jextract+2-4") // Optional: defaults to a stable version
    
    libraries {
        create("opengl") {
            headerFile.set(file("src/main/c/gl.h"))
            targetPackage.set("com.example.gl")
        }
    }
}
```

This will:
1. Download jextract to the Gradle user home cache (if needed)
2. Create a task named `generateOpenglBindings`
3. Generate bindings in `build/generated/sources/jextract/opengl/`
4. Automatically add the generated sources to the main source set

### Multiple Libraries

Generate bindings for multiple libraries:

```kotlin
jextract {
    libraries {
        create("opengl") {
            headerFile.set(file("src/main/c/gl.h"))
            targetPackage.set("com.example.gl")
        }
        
        create("audio") {
            headerFile.set(file("src/main/c/audio.h"))
            targetPackage.set("com.example.audio")
        }
    }
}
```

This creates separate tasks:
- `generateOpenglBindings`
- `generateAudioBindings`

### Advanced Configuration

#### Custom Header Class Name

```kotlin
jextract {
    libraries {
        create("mylib") {
            headerFile.set(file("src/main/c/mylib.h"))
            targetPackage.set("com.example.mylib")
            headerClassName.set("MyLibBindings") // Custom class name
        }
    }
}
```

#### Compiler Arguments

Pass additional arguments to jextract:

```kotlin
jextract {
    libraries {
        create("mylib") {
            headerFile.set(file("src/main/c/mylib.h"))
            targetPackage.set("com.example.mylib")
            compilerArgs.set(listOf(
                "-I", "/usr/local/include",
                "--include-function", "specific_function"
            ))
        }
    }
}
```

### Library Loading Configuration

The plugin supports three ways to configure native library loading for your Java bindings. **All options are completely optional** - if you don't configure any, you'll need to load the library manually in your code.

#### Option 1: System Libraries

Use `libraryName` for libraries installed on the system. This is **cross-platform** because jextract uses `System.mapLibraryName()` to convert library names at runtime:

```kotlin
jextract {
    libraries {
        create("opengl") {
            headerFile.set(file("src/main/c/gl.h"))
            targetPackage.set("com.example.gl")
            libraryName.set("GL")  // Cross-platform!
        }
    }
}
```

**How it works:**
- On **Linux**: loads `libGL.so`
- On **macOS**: loads `libGL.dylib`
- On **Windows**: loads `GL.dll`

The library must be on the system's library search path (`LD_LIBRARY_PATH` on Linux, `DYLD_LIBRARY_PATH` on macOS, or `PATH` on Windows).

#### Option 2: JAR-Bundled Libraries

Use `nativeLibraryLoading` to bundle platform-specific libraries in your JAR and automatically extract/load them at runtime:

```kotlin
jextract {
    libraries {
        create("mylib") {
            headerFile.set(file("src/main/c/mylib.h"))
            targetPackage.set("com.example.mylib")
            
            nativeLibraryLoading {
                // Template for resource path in JAR
                // Variables {os.name} and {os.arch} are expanded at runtime
                resourcePath.set("native/{os.name}-{os.arch}/mylib")
                
                // Optional: Enable caching of extracted libraries (default: false)
                enableCaching.set(true)
            }
        }
    }
}
```

**Resource Path Variables:**
- `{os.name}`: `linux`, `windows`, `macos`
- `{os.arch}`: `amd64` (x86_64), `aarch64` (ARM64)

**Example Project Structure:**
```
src/main/resources/
└── native/
    ├── linux-amd64/
    │   └── libmylib.so
    ├── windows-amd64/
    │   └── mylib.dll
    └── macos-aarch64/
        └── libmylib.dylib
```

**Best for:** Distributing self-contained applications or libraries where the native code is packaged with the Java code.

#### Option 3: Manual Library Loading (No Configuration)

If you don't configure any library loading option, the plugin generates bindings that don't attempt to load any library. You must load it manually before using any generated methods:

```kotlin
create("mylib") {
    headerFile.set(file("src/main/c/mylib.h"))
    targetPackage.set("com.example.mylib")
    // No library configuration
}
```

In your Java code:
```java
static {
    System.load("/absolute/path/to/libmylib.so");
}
```

## Tasks

For each library named `{name}`, the plugin creates a task `generate{Name}Bindings`:

```bash
# Generate bindings for a specific library
./gradlew generateOpenglBindings

# Generate all bindings
./gradlew generateOpenglBindings generateAudioBindings

# Build the project (automatically runs all binding generation tasks)
./gradlew build
```

The generated tasks are automatically integrated with the Java compilation, so running `compileJava` will trigger binding generation as needed.

## Output Structure

Generated bindings are placed in:

```
build/
└── generated/
    └── sources/
        └── jextract/
            └── {libraryName}/
                └── com/
                    └── example/
                        └── ... (generated Java files)
```

## Troubleshooting

### Build fails with "jextract not found"

The plugin automatically downloads jextract. If you see this error:
- Check your internet connection
- Verify the `toolVersion` is a valid jextract release
- Check `~/.gradle/caches/jextract-tool/` for download issues

### Generated bindings not found in IDE

After generating bindings:
1. Run `./gradlew build` to ensure generation completes
2. Refresh your IDE project (e.g., `Gradle → Reload All Gradle Projects` in IntelliJ)

### Incremental builds not working

The plugin tracks:
- Header file content changes
- Configuration changes (package name, compiler args, etc.)
- Jextract version changes

Clean builds when needed:
```bash
./gradlew clean build
```

## Contributing

Contributions are welcome! Please submit issues and pull requests on the [GitHub repository](https://github.com/TimSchoenle/gradle-jextract).

## License

This project is licensed under the [Apache License 2.0](LICENSE).
