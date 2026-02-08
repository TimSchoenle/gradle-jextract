package de.timscho.jextract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class JextractPluginFunctionalTest extends AbstractJextractFunctionalTest {

    @Test
    void canGenerateBindingsForMultipleLibraries() throws IOException {
        // Arrange
        final Path cDir = this.testProjectDir.resolve("src/main/c");
        Files.createDirectories(cDir);
        Files.writeString(cDir.resolve("gl.h"), "void render();");
        Files.writeString(cDir.resolve("audio.h"), "void play();");

        final String version = "22-ea+5";
        // Use a stable path for Gradle User Home to avoid Windows file locking issues during cleanup
        final Path stableUserHome = Path.of("build/functionalTest/mock-home-" + System.nanoTime());
        Files.createDirectories(stableUserHome);

        this.mockJextractTool(stableUserHome, version);

        this.writeBuildScript(
                version,
                JextractTestUtils.LibraryDefinition.builder()
                        .name("opengl")
                        .headerFile("src/main/c/gl.h")
                        .targetPackage("com.gl")
                        .build(),
                JextractTestUtils.LibraryDefinition.builder()
                        .name("audio")
                        .headerFile("src/main/c/audio.h")
                        .targetPackage("com.audio")
                        .build());
        // settings.gradle is already created by base setup

        // Act
        final BuildResult result = this.createRunner(stableUserHome).build();

        // Assert
        assertThat(result.task(":generateOpenglBindings").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":generateAudioBindings").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        final Path openglOut = this.testProjectDir.resolve("build/generated/sources/jextract/opengl/com/gl/gl_h.java");
        final Path audioOut =
                this.testProjectDir.resolve("build/generated/sources/jextract/audio/com/audio/audio_h.java");

        assertThat(openglOut)
                .as("OpenGL bindings should have been generated at " + openglOut)
                .exists();
        assertThat(audioOut)
                .as("Audio bindings should have been generated at " + audioOut)
                .exists();
    }
}
