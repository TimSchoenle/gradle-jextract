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
     * <p>{@code {os.name}} and {@code {os.arch}} are substituted from the JVM running the bindings
     * rather than the one that built them, and the last segment is then spelled the way that
     * platform spells a library, so {@code native/{os.name}-{os.arch}/mylib} reads
     * {@code /native/linux-amd64/libmylib.so}. Setting this is what makes the task generate a
     * loader at all.
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

    /** {@return the extraction directory as plain text} */
    // Not Javadoc: the directory itself is @Internal because it is a runtime location the build
    // never reads or writes. Its path is still an input, since it ends up inside the generated
    // source, and this is the property that puts it there. A caller does nothing with either fact.
    @Input
    @Optional
    protected org.gradle.api.provider.Provider<String> getExtractionDirPath() {
        return this.getExtractionDir().map(directory -> directory.getAsFile().getPath());
    }

    /**
     * {@return whether the generated loader hashes the resource before extracting it}
     *
     * <p>False by default, which extracts once per JVM start. True adds a SHA-256 pass over the
     * resource and a lookup for {@code <file>.<hash>} in the extraction directory, a name the
     * extraction does not write, so the lookup misses and the file is extracted anyway.
     */
    @Input
    @Optional
    public abstract Property<Boolean> getEnableCaching();
}
