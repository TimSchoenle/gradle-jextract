package de.timscho.jextract.extension;

import javax.inject.Inject;
import lombok.Getter;
import org.gradle.api.Action;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

/**
 * One C library to generate bindings for.
 *
 * <p>A header file and a target package are the two properties with no default; the rest may be
 * left out. The name is not one of them, and it is not cosmetic: it names the task, and it names
 * the output directory, so renaming an entry renames both and regenerates from scratch.
 */
@Getter
public abstract class JextractDefinition {
    /** {@return the name this declaration was created under} */
    private final String name;

    private final NativeLibraryLoadingConfig nativeLibraryLoading;

    /**
     * Creates a declaration, which the container does for every name a build script adds.
     *
     * @param name identifies the declaration and derives the task and output directory names
     * @param objectFactory instantiates the nested native library loading block
     */
    @Inject
    public JextractDefinition(final String name, final ObjectFactory objectFactory) {
        this.name = name;
        this.nativeLibraryLoading = objectFactory.newInstance(NativeLibraryLoadingConfig.class);
    }

    /**
     * {@return the {@code .h} file jextract parses}
     *
     * <p>Tracked by content and not by path, so moving the header between directories leaves the
     * task up to date.
     */
    public abstract RegularFileProperty getHeaderFile();

    /** {@return the package the generated classes are written into} */
    public abstract Property<String> getTargetPackage();

    /**
     * {@return arguments handed to jextract unchanged}
     *
     * <p>Placed after the arguments the plugin generates and before the header path, so an
     * {@code --include-function} filter here applies and an {@code --output} here is a duplicate.
     */
    public abstract ListProperty<String> getCompilerArgs();

    /**
     * {@return the name of the class jextract puts the top-level declarations on}
     *
     * <p>Unset, the header file name is used with {@code .h} replaced by {@code _h}.
     */
    public abstract Property<String> getHeaderClassName();

    /**
     * {@return a library the machine running the bindings already has, named without prefix or
     * extension}
     *
     * <p>Reaches jextract as {@code -l}, which spells it for the platform, so {@code GL} resolves
     * {@code libGL.so}, {@code libGL.dylib} or {@code GL.dll} through that platform's library
     * search path. Mutually exclusive with {@link #getNativeLibraryLoading()}: a declaration
     * setting both fails when the task runs.
     */
    @Optional
    public abstract Property<String> getLibraryName();

    /**
     * {@return the block that ships the library inside the JAR and generates a loader for it}
     *
     * <p>Inert until its {@code resourcePath} is set.
     */
    @Nested
    @Optional
    public NativeLibraryLoadingConfig getNativeLibraryLoading() {
        return this.nativeLibraryLoading;
    }

    /**
     * Configures loading the library out of a JAR resource.
     *
     * @param action applied to the nested block
     */
    public void nativeLibraryLoading(final Action<? super NativeLibraryLoadingConfig> action) {
        action.execute(this.nativeLibraryLoading);
    }
}
