package de.timscho.jextract.extension;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

/**
 * Settings for a native library that travels inside the JAR and is extracted at first use.
 *
 * <p>Every value here is resolved at build time and written into the generated loader, so a change
 * takes effect by regenerating and not by anything the running application can pass in.
 */
public abstract class NativeLibraryLoadingConfig {
    /** Creates the block, which the object factory does for every library declaration. */
    public NativeLibraryLoadingConfig() {}

    /**
     * {@return the resource holding the library, as a template carrying neither the platform's
     * library prefix nor its extension}
     *
     * <p>{@code {os.name}} expands to {@code windows}, {@code macos} or {@code linux} and
     * {@code {os.arch}} to {@code amd64}, {@code aarch64} or {@code x86}, both read from the JVM
     * running the bindings rather than the one that built them. The last segment is then spelled
     * the way that platform spells a library, which is why the template carries neither:
     * {@code native/{os.name}-{os.arch}/mylib} reads {@code /native/linux-amd64/libmylib.so}.
     *
     * <p>Setting this is what makes the task generate a loader at all. An architecture outside the
     * three throws {@code UnsupportedOperationException} naming what it found.
     */
    @Input
    @Optional
    public abstract Property<String> getResourcePath();

    /**
     * {@return the directory the extracted library is written to}
     *
     * <p>The absolute path is baked into the generated source, so a directory that exists on the
     * build machine and not on the target machine is a runtime failure rather than a build one.
     * Unset, the loader creates {@code jextract-natives} under {@code java.io.tmpdir} wherever it
     * runs.
     */
    @org.gradle.api.tasks.Internal
    public abstract DirectoryProperty getExtractionDir();

    /**
     * {@return the extraction directory as plain text}
     *
     * <p>The directory itself is {@code @Internal} because it is a runtime location the build never
     * reads or writes. Its path is still an input, since it ends up inside the generated source,
     * and this is the property that puts it there.
     */
    @Input
    @Optional
    protected org.gradle.api.provider.Provider<String> getExtractionDirPath() {
        return this.getExtractionDir().map(directory -> directory.getAsFile().getPath());
    }

    /**
     * {@return whether the loader looks for an already-extracted copy before writing one}
     *
     * <p>Adds a SHA-256 pass over the resource to the generated loader, which then loads
     * {@code <file>.<hash>} from the extraction directory when that file is present. False by
     * default, which extracts once per JVM start.
     */
    @Input
    @Optional
    public abstract Property<Boolean> getEnableCaching();
}
