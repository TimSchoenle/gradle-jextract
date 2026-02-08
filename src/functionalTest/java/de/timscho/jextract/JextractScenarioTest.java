package de.timscho.jextract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class JextractScenarioTest extends AbstractJextractFunctionalTest {

    @Test
    void incrementalBuildSkipsUpToDateTasks() throws IOException {
        // Arrange
        final Path cDir = this.testProjectDir.resolve("src/main/c");
        Files.createDirectories(cDir);
        Files.writeString(cDir.resolve("math.h"), "int add(int a, int b);");

        final String version = "22-ea+5";
        final Path stableUserHome = Path.of("build/functionalTest/scenario-home-" + System.nanoTime());
        Files.createDirectories(stableUserHome);
        this.mockJextractTool(stableUserHome, version);

        this.writeBuildScript(
                version,
                JextractTestUtils.LibraryDefinition.builder()
                        .name("math")
                        .headerFile("src/main/c/math.h")
                        .targetPackage("com.math")
                        .build());

        // Act (Initial Build)
        final BuildResult result1 = this.createRunner(stableUserHome).build();

        // Assert (Initial Build)
        assertThat(result1.task(":generateMathBindings").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        // Act (Second Build - No changes)
        final BuildResult result2 = this.createRunner(stableUserHome).build();

        // Assert (Second Build)
        assertThat(result2.task(":generateMathBindings").getOutcome()).isEqualTo(TaskOutcome.UP_TO_DATE);

        // Act (Modify Header)
        Files.writeString(cDir.resolve("math.h"), "int sub(int a, int b);");

        // Act (Third Build)
        final BuildResult result3 = this.createRunner(stableUserHome).build();

        // Assert (Third Build)
        assertThat(result3.task(":generateMathBindings").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }

    @Test
    void taskDependenciesAreSetCorrectly() throws IOException {
        // Arrange
        final Path cDir = this.testProjectDir.resolve("src/main/c");
        Files.createDirectories(cDir);
        Files.writeString(cDir.resolve("dep.h"), "void dep();");

        final String version = "22-ea+5";
        final Path stableUserHome = Path.of("build/functionalTest/scenario-dep-home-" + System.nanoTime());
        Files.createDirectories(stableUserHome);
        this.mockJextractTool(stableUserHome, version);

        this.writeBuildScript(
                version,
                JextractTestUtils.LibraryDefinition.builder()
                        .name("dep")
                        .headerFile("src/main/c/dep.h")
                        .targetPackage("com.dep")
                        .build());

        // Act
        final BuildResult result = this.createRunner(stableUserHome)
                .withArguments(
                        "clean",
                        "compileJava",
                        "--stacktrace",
                        "--gradle-user-home",
                        stableUserHome.toAbsolutePath().toString())
                .build();

        // Assert
        assertThat(result.task(":generateDepBindings").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.task(":compileJava").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
    }
}
