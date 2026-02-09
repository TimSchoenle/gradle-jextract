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

@Getter
public abstract class JextractDefinition {
    private final String name;
    private final NativeLibraryLoadingConfig nativeLibraryLoading;

    @Inject
    public JextractDefinition(final String name, final ObjectFactory objectFactory) {
        this.name = name;
        this.nativeLibraryLoading = objectFactory.newInstance(NativeLibraryLoadingConfig.class);
    }

    public abstract RegularFileProperty getHeaderFile();

    public abstract Property<String> getTargetPackage();

    public abstract ListProperty<String> getCompilerArgs();

    public abstract Property<String> getHeaderClassName();

    /**
     * Library name for system-installed libraries.
     * Uses System.mapLibraryName() at build time for jextract.
     * Example: "GL" → jextract receives "-l GL"
     *
     * @return the library name property
     */
    @Optional
    public abstract Property<String> getLibraryName();

    /**
     * Returns the nested configuration for loading native libraries from JAR resources.
     *
     * @return the nested configuration
     */
    @Nested
    @Optional
    public NativeLibraryLoadingConfig getNativeLibraryLoading() {
        return this.nativeLibraryLoading;
    }

    /**
     * Configures native library loading from JAR resources using a closure/action.
     *
     * @param action the closure/action to configure the native library loading
     */
    public void nativeLibraryLoading(final Action<? super NativeLibraryLoadingConfig> action) {
        action.execute(this.nativeLibraryLoading);
    }
}
