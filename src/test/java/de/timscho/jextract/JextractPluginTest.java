package de.timscho.jextract;

import static org.assertj.core.api.Assertions.assertThat;

import de.timscho.jextract.extension.JextractExtension;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class JextractPluginTest {

    @Test
    void pluginRegistersExtensionAndService() {
        // Arrange
        final Project project = ProjectBuilder.builder().build();

        // Act
        project.getPluginManager().apply("de.timscho.jextract");

        // Assert
        final JextractExtension extension = project.getExtensions().findByType(JextractExtension.class);
        assertThat(extension).as("JextractExtension should be registered").isNotNull();

        assertThat(extension.getToolVersion().getOrNull())
                .as("Default tool version should be set")
                .isEqualTo("25-jextract+2-4");
    }

    @Test
    void pluginRegistersTasksForLibraries() {
        // Arrange
        final Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("de.timscho.jextract");
        project.getPluginManager().apply("java"); // Needed for SourceSet integration

        final JextractExtension extension = project.getExtensions().getByType(JextractExtension.class);

        // Act
        extension.libraries(libs -> {
            libs.register("opengl", lib -> {
                lib.getHeaderFile().set(project.file("gl.h"));
                lib.getTargetPackage().set("com.gl");
            });
        });

        // Assert
        assertThat(project.getTasks().findByName("generateOpenglBindings"))
                .as("Task should be registered for 'opengl' library")
                .isNotNull();
    }
}
