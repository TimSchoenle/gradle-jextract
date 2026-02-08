package de.timscho.jextract.extension;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Configuration for loading native libraries bundled in JAR resources.
 * This allows libraries to be packaged with the application and extracted at runtime.
 */
public abstract class NativeLibraryLoadingConfig {

    /**
     * Resource path template for the native library.
     * Supports variables: {os.name} and {os.arch}
     * Example: "native/{os.name}-{os.arch}/mylib"
     *
     * <p>At runtime, this expands to platform-specific paths:
     * <ul>
     * <li>Linux x64: native/linux-amd64/libmylib.so</li>
     * <li>Windows x64: native/windows-amd64/mylib.dll</li>
     * <li>macOS ARM64: native/macos-aarch64/libmylib.dylib</li>
     * </ul>
     */
    @Input
    @Optional
    public abstract Property<String> getResourcePath();

    /**
     * Directory where extracted libraries are stored.
     * Default: system temp directory (java.io.tmpdir)
     */
    @org.gradle.api.tasks.Internal
    public abstract DirectoryProperty getExtractionDir();

    @Input
    @Optional
    protected org.gradle.api.provider.Provider<String> getExtractionDirPath() {
        return this.getExtractionDir().map(directory -> directory.getAsFile().getPath());
    }

    /**
     * Enable caching of extracted libraries across JVM runs.
     * When enabled, libraries are extracted once and reused if the hash matches.
     * Default: false (extract fresh each time)
     */
    @Input
    @Optional
    public abstract Property<Boolean> getEnableCaching();
}
