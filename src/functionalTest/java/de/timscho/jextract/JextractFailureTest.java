package de.timscho.jextract;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.UnexpectedBuildFailure;
import org.junit.jupiter.api.Test;

class JextractFailureTest extends AbstractJextractFunctionalTest {

    @Test
    void failsBuildWithMissingHeaderFile() throws IOException {
        // Arrange
        final String version = "22-ea+5";
        Path stableUserHome = Path.of("build/functionalTest/failure-home-" + System.nanoTime());
        Files.createDirectories(stableUserHome);
        mockJextractTool(stableUserHome, version);

        writeBuildScript(
                version,
                JextractTestUtils.LibraryDefinition.builder()
                        .name("missing")
                        .headerFile("src/main/c/does-not-exist.h")
                        .targetPackage("com.missing")
                        .build());

        // Act & Assert
        assertThatThrownBy(() -> createRunner(stableUserHome).build())
                .isInstanceOf(UnexpectedBuildFailure.class)
                .hasMessageContaining("does-not-exist.h");
    }
}
