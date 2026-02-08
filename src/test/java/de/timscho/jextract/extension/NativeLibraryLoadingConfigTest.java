package de.timscho.jextract.extension;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class NativeLibraryLoadingConfigTest {

    @Test
    void defaultValuesAreEmpty() {
        // Arrange
        Project project = ProjectBuilder.builder().build();
        NativeLibraryLoadingConfig config = project.getObjects().newInstance(NativeLibraryLoadingConfig.class);

        // Act & Assert
        assertThat(config.getResourcePath().isPresent())
                .as("Resource path should be empty by default")
                .isFalse();
    }

    @Test
    void canSetResourcePath() {
        // Arrange
        Project project = ProjectBuilder.builder().build();
        NativeLibraryLoadingConfig config = project.getObjects().newInstance(NativeLibraryLoadingConfig.class);

        // Act
        config.getResourcePath().set("/libs/mylib.so");

        // Assert
        assertThat(config.getResourcePath().get())
                .as("Resource path should be set")
                .isEqualTo("/libs/mylib.so");
    }
}
