package de.timscho.jextract.extension;

import javax.inject.Inject;
import lombok.Getter;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

@Getter
public abstract class JextractExtension {
    private final NamedDomainObjectContainer<JextractDefinition> libraries;

    @Inject
    public JextractExtension(final ObjectFactory objectFactory) {
        this.libraries = objectFactory.domainObjectContainer(JextractDefinition.class);
    }

    public abstract Property<String> getToolVersion();

    /**
     * Configures native library loading from JAR resources using a closure/action.
     *
     * @param action the closure/action to configure the native library loading
     */
    public void libraries(final Action<? super NamedDomainObjectContainer<JextractDefinition>> action) {
        action.execute(this.libraries);
    }
}
