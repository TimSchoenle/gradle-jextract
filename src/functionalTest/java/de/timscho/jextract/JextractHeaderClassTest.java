package de.timscho.jextract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class JextractHeaderClassTest extends AbstractJextractFunctionalTest {

    @Test
    void canConfigureHeaderClassName() throws IOException {
        // Arrange
        final Path cDir = this.testProjectDir.resolve("src/main/c");
        Files.createDirectories(cDir);
        Files.writeString(cDir.resolve("test.h"), "void test();");

        final String version = "25-jextract+2-4";
        final Path userHome = Path.of("build/functionalTest/mock-home-header-" + System.nanoTime());
        Files.createDirectories(userHome);
        this.mockJextractTool(userHome, version);

        this.writeBuildScript(
                version,
                JextractTestUtils.LibraryDefinition.builder()
                        .name("mylib")
                        .headerFile("src/main/c/test.h")
                        .targetPackage("com.example")
                        .headerClassName("MyCustomHeader")
                        .build());

        // Act
        final BuildResult result = this.createRunner(userHome).build();

        // Assert
        assertThat(result.task(":generateMylibBindings").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        final Path debugLog = this.testProjectDir.resolve("jextract-mock.log");
        assertThat(debugLog).as("Debug log should exist").exists();
        final String logContent = Files.readString(debugLog);

        assertThat(logContent)
                .as("Log should contain header class name arg. Content: " + logContent)
                .satisfiesAnyOf(
                        content -> assertThat(content).contains("--header-class-name MyCustomHeader"),
                        content -> assertThat(content).contains("--header-class-name, MyCustomHeader"));
    }
}
