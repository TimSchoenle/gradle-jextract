package de.timscho.jextract.extension;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class JextractDefinitionTest {

    @Test
    void canConfigureDefinition() {
        // Arrange
        Project project = ProjectBuilder.builder().build();
        JextractDefinition definition = project.getObjects().newInstance(JextractDefinition.class, "testLib");

        // Act
        definition.getHeaderFile().set(project.file("test.h"));
        definition.getTargetPackage().set("com.example");
        definition.getHeaderClassName().set("TestHeader");
        definition.getCompilerArgs().add("-I/usr/include");
        definition.getLibraryName().set("test");

        // Assert
        assertThat(definition.getName()).isEqualTo("testLib");
        assertThat(definition.getHeaderFile().get().getAsFile()).hasName("test.h");
        assertThat(definition.getTargetPackage().get()).isEqualTo("com.example");
        assertThat(definition.getHeaderClassName().get()).isEqualTo("TestHeader");
        assertThat(definition.getCompilerArgs().get()).containsExactly("-I/usr/include");
        assertThat(definition.getLibraryName().get()).isEqualTo("test");
    }

    @Test
    void canConfigureNativeLibraryLoading() {
        // Arrange
        Project project = ProjectBuilder.builder().build();
        JextractDefinition definition = project.getObjects().newInstance(JextractDefinition.class, "testLib");

        // Act
        definition.getNativeLibraryLoading().getResourcePath().set("/libs/test.utils");

        // Assert
        assertThat(definition.getNativeLibraryLoading().getResourcePath().get()).isEqualTo("/libs/test.utils");
    }
}
