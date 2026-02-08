package de.timscho.jextract.extension;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class JextractExtensionTest {

    @Test
    void canConfigureLibraries() {
        // Arrange
        Project project = ProjectBuilder.builder().build();
        JextractExtension extension = project.getExtensions().create("jextract", JextractExtension.class);

        // Act
        extension.libraries(libs -> {
            libs.register("lib1", lib -> {
                lib.getHeaderFile().set(project.file("lib1.h"));
                lib.getTargetPackage().set("com.lib1");
            });
            libs.register("lib2", lib -> {
                lib.getHeaderFile().set(project.file("lib2.h"));
                lib.getTargetPackage().set("com.lib2");
            });
        });

        // Assert
        assertThat(extension.getLibraries()).hasSize(2);

        JextractDefinition lib1 = extension.getLibraries().getByName("lib1");
        assertThat(lib1.getTargetPackage().get()).isEqualTo("com.lib1");

        JextractDefinition lib2 = extension.getLibraries().getByName("lib2");
        assertThat(lib2.getTargetPackage().get()).isEqualTo("com.lib2");
    }
}
