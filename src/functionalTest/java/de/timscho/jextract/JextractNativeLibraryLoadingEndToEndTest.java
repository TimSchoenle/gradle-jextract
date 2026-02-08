package de.timscho.jextract;


import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JextractNativeLibraryLoadingEndToEndTest extends AbstractJextractFunctionalTest {

    private Path gradleUserHome;

    @BeforeEach
    @Override
    void setup() throws IOException {
        super.setup();
        this.gradleUserHome = Files.createTempDirectory("gradle-user-home");
    }

    @AfterEach
    void cleanup() {
        try {
            this.deleteDirectory(this.gradleUserHome);
        } catch (final Exception exception) {
            System.err.println("Warning: Failed to cleanup temp dir: " + exception.getMessage());
        }
    }

    @Test
    void endToEndNativeLibraryLoading() throws Exception {
        // Check if GCC is available
        Assumptions.assumeTrue(this.isGccAvailable(), "GCC is not available, skipping native test");

        // Determine OS and Architecture
        final String osNameProperty = System.getProperty("os.name").toLowerCase();
        final String osArch = System.getProperty("os.arch").toLowerCase();

        final String platformName;
        final String libExtension;

        if (osNameProperty.contains("win")) {
            platformName = "windows";
            libExtension = ".dll";
        } else if (osNameProperty.contains("mac")) {
            platformName = "macos";
            libExtension = ".dylib";
        } else {
            platformName = "linux";
            libExtension = ".so";
        }

        // Arrange
        final Path cSource = this.testProjectDir.resolve("src/main/c/math_lib.c");
        final Path cHeader = this.testProjectDir.resolve("src/main/c/math_lib.h");
        Files.createDirectories(cSource.getParent());

        this.copyResource("native-test/math_lib.h", cHeader);
        this.copyResource("native-test/math_lib.c", cSource);

        // Prepare output directory for the shared library
        final String resourcePath = "native/" + platformName + "-" + osArch;
        final Path libDir = this.testProjectDir.resolve("src/main/resources/" + resourcePath);
        Files.createDirectories(libDir);

        final String libName = "math_lib" + libExtension;
        final Path libFile = libDir.resolve(libName);

        this.compileSharedLibrary(cSource, libFile);

        // We instruct the plugin to include the library we just built
        final JextractTestUtils.LibraryDefinition lib = JextractTestUtils.LibraryDefinition.builder()
                .name("math")
                .headerFile("src/main/c/math_lib.h")
                .targetPackage("com.example.math")
                .nativeLibraryResourcePath(resourcePath + "/math_lib") // Implicit extension handling
                .nativeLibraryExtractionDir("build/jextract-natives")
                .build();

        this.writeBuildScript(null, lib); // Use default version (likely 25-jextract+2-4)

        // Create a Java Test to Verify Loading
        // This test runs INSIDE the Gradle build as a 'test' task
        final Path javaTestDir = this.testProjectDir.resolve("src/test/java/com/example/math");
        Files.createDirectories(javaTestDir);

        this.copyResource("native-test/MathTest.java", javaTestDir.resolve("MathTest.java"));

        // We need dependencies for JUnit in the generated project
        // Append dependencies to build.gradle
        Files.writeString(this.buildFile.toPath(), Files.readString(this.buildFile.toPath()) + """

            repositories {
                mavenCentral()
            }

            testing {
                suites {
                    test {
                        useJUnitJupiter('5.10.0')
                    }
                }
            }

            // Ensure test task runs and shows output
            test {
                testLogging {
                    events "passed", "skipped", "failed", "standardOut", "standardError"
                    showStandardStreams = true
                }
            }
        """);

        // Act
        // We use --info to see what's happening
        final BuildResult result = this.createRunner(this.gradleUserHome)
                .withArguments("build", "--info", "--stacktrace")
                .build();

        // Assert
        assertThat(result.task(":test").getOutcome()).isEqualTo(TaskOutcome.SUCCESS);
        assertThat(result.getOutput()).contains("Native add(10, 20) returned: 30");
    }

    private boolean isGccAvailable() {
        try {
            final Process process = new ProcessBuilder("gcc", "--version").start();
            final boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (final IOException | InterruptedException e) {
            return false;
        }
    }

    private void compileSharedLibrary(final Path source, final Path output) throws IOException, InterruptedException {
        final String osName = System.getProperty("os.name").toLowerCase();
        final List<String> command = new ArrayList<>();
        command.add("gcc");

        if (osName.contains("win")) {
            command.add("-shared");
            command.add("-o");
            command.add(output.toAbsolutePath().toString());
            command.add(source.toAbsolutePath().toString());
        } else if (osName.contains("mac")) {
            command.add("-dynamiclib");
            command.add("-o");
            command.add(output.toAbsolutePath().toString());
            command.add(source.toAbsolutePath().toString());
        } else {
            // Linux and others
            command.add("-shared");
            command.add("-fPIC"); // Position Independent Code
            command.add("-o");
            command.add(output.toAbsolutePath().toString());
            command.add(source.toAbsolutePath().toString());
        }

        final ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO(); // Show compiler output
        final Process process = pb.start();
        final int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new RuntimeException("GCC compilation failed with exit code: " + exitCode);
        }
    }
}
