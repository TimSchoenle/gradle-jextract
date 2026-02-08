package de.timscho.jextract.task;

import static org.assertj.core.api.Assertions.assertThat;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class JextractTaskTest {

    @Test
    void canConfigureTaskProperties() {
        // Arrange
        final Project project = ProjectBuilder.builder().build();
        final JextractTask task = project.getTasks().create("jextract", JextractTask.class);

        // Act
        task.getHeaderFile().set(project.file("test.h"));
        task.getTargetPackage().set("com.example");
        task.getHeaderClassName().set("TestHeader");
        task.getLibraryName().set("testlib");
        task.getCompilerArgs().add("-I/usr/include");

        // Assert
        assertThat(task.getHeaderFile().get().getAsFile()).hasName("test.h");
        assertThat(task.getTargetPackage().get()).isEqualTo("com.example");
        assertThat(task.getHeaderClassName().get()).isEqualTo("TestHeader");
        assertThat(task.getLibraryName().get()).isEqualTo("testlib");
        assertThat(task.getCompilerArgs().get()).containsExactly("-I/usr/include");
    }
}
