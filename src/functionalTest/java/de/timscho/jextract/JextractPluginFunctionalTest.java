package de.timscho.jextract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

public class JextractPluginFunctionalTest extends AbstractJextractFunctionalTest {

    @Test
    void canGenerateBindingsForMultipleLibraries() throws IOException {
        // 1. Create Dummy Header Files
        final Path cDir = testProjectDir.resolve("src/main/c");
        Files.createDirectories(cDir);
        Files.writeString(cDir.resolve("gl.h"), "void render();");
        Files.writeString(cDir.resolve("audio.h"), "void play();");

        // 2. Mock the Jextract Tool
        final String version = "22-ea+5";
        // Use a stable path for Gradle User Home to avoid Windows file locking issues during cleanup
        final Path stableUserHome = Path.of("build/functionalTest/mock-home-" + System.nanoTime());
        Files.createDirectories(stableUserHome);

        mockJextractTool(stableUserHome, version);

        // 3. Create Build Script
        writeBuildScript(
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

        // 4. Run the Build
        final BuildResult result = createRunner(stableUserHome).build();

        // 5. Verifications
        // 5. Verifications
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateOpenglBindings").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, result.task(":generateAudioBindings").getOutcome());

        // Update to look for correctly named files in package structure
        final Path openglOut = testProjectDir.resolve("build/generated/sources/jextract/opengl/com/gl/gl_h.java");
        final Path audioOut = testProjectDir.resolve("build/generated/sources/jextract/audio/com/audio/audio_h.java");

        assertTrue(Files.exists(openglOut), "OpenGL bindings should have been generated at " + openglOut);
        assertTrue(Files.exists(audioOut), "Audio bindings should have been generated at " + audioOut);
    }
}
