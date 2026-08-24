package de.timscho.jextract.extension;

import javax.inject.Inject;
import lombok.Getter;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

/** Project-wide jextract settings, and the libraries bindings are generated for. */
@Getter
public abstract class JextractExtension {
    /**
     * {@return the libraries declared in this project, keyed by the name each was created under}
     *
     * <p>The container is empty until a build script adds to it, and the plugin reacts to every
     * addition, so an entry registered late still gets its task.
     */
    private final NamedDomainObjectContainer<JextractDefinition> libraries;

    /**
     * Creates the extension, which Gradle does when the plugin is applied.
     *
     * @param objectFactory instantiates the container and each declaration added to it
     */
    @Inject
    public JextractExtension(final ObjectFactory objectFactory) {
        this.libraries = objectFactory.domainObjectContainer(JextractDefinition.class);
    }

    /**
     * {@return the jextract early-access build to download}
     *
     * <p>Matched against {@code <major>-jextract+<build>}, optionally followed by a hyphen and a
     * refinement, because the download URL is assembled out of the major and the build; a string
     * that does not match fails before anything is fetched. One value covers the whole project, so
     * declaring six libraries costs one download. Defaults to the build this release was compiled
     * against.
     */
    public abstract Property<String> getToolVersion();

    /**
     * Configures the libraries to generate bindings for.
     *
     * <p>Each name added to the container becomes a {@code generate<Name>Bindings} task and the
     * directory under {@code build/generated/sources/jextract} that its output lands in.
     *
     * @param action applied to the container of declarations
     */
    public void libraries(final Action<? super NamedDomainObjectContainer<JextractDefinition>> action) {
        action.execute(this.libraries);
    }
}
