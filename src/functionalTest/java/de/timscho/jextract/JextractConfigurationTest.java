package de.timscho.jextract;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

class JextractConfigurationTest extends AbstractJextractFunctionalTest {

    @Test
    void supportsCustomTargetPackageAndCompilerArgs() throws IOException {
        // Arrange
        final Path cDir = testProjectDir.resolve("src/main/c");
        Files.createDirectories(cDir);
        // Header that requires a define to work
        Files.writeString(
                cDir.resolve("config.h"), "#ifndef MY_DEFINE\n#error MY_DEFINE missing\n#endif\nvoid config();");

        final String version = "22-ea+5";
        Path stableUserHome = Path.of("build/functionalTest/config-home-" + System.nanoTime());
        Files.createDirectories(stableUserHome);
        mockJextractTool(stableUserHome, version);

        writeBuildScript(
                version,
                JextractTestUtils.LibraryDefinition.builder()
                        .name("configLib")
                        .headerFile("src/main/c/config.h")
                        .targetPackage("com.example.custom")
                        .compilerArg("-DMY_DEFINE")
                        .build());

        // Act
        BuildResult result = createRunner(stableUserHome).build();

        // Assert
        assertThat(result.task(":generateConfigLibBindings").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);

        Path out =
                testProjectDir.resolve("build/generated/sources/jextract/configLib/com/example/custom/config_h.java");
        assertThat(out).as("File should exist at " + out).exists();

        Path dbgLog = testProjectDir.resolve("jextract-mock.log");
        String content = Files.readString(dbgLog);
        assertThat(content).as("Should contain custom package").contains("com.example.custom");
        assertThat(content).as("Should contain define").contains("-DMY_DEFINE");
    }
}
