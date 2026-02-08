package de.timscho.jextract;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JextractNativeLibraryLoadingTest extends AbstractJextractFunctionalTest {
    Path gradleUserHome;

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
    void generatesNativeLibraryLoaderWithBasicConfiguration() throws IOException {
        // Arrange
        final Path headerFile = this.testProjectDir.resolve("native.h");
        this.copyResource("native-loading/basic/native.h", headerFile);

        final JextractTestUtils.LibraryDefinition lib = JextractTestUtils.LibraryDefinition.builder()
                .name("nativeLib")
                .headerFile("native.h")
                .targetPackage("com.example.native_lib")
                .nativeLibraryResourcePath("native/{os.name}-{os.arch}/mynative")
                .build();

        this.writeBuildScript("25-jextract+2-4", lib);
        this.mockJextractTool(this.gradleUserHome, "25-jextract+2-4");

        // Act
        final BuildResult result = this.createRunner(this.gradleUserHome).build();

        // Assert
        assertThat(result.getOutput()).as("Build should succeed").contains("BUILD SUCCESSFUL");

        final Path loaderFile = this.projectDir
                .toPath()
                .resolve(
                        "build/generated/sources/jextract/nativeLib/com/example/native_lib/native_h_NativeLibraryLoader.java");
        assertThat(loaderFile).as("Loader file should exist").exists();

        final JavaParser parser = new JavaParser();
        final ParseResult<CompilationUnit> parseResult = parser.parse(loaderFile);
        assertThat(parseResult.isSuccessful())
                .as("Generated code should parse successfully")
                .isTrue();

        final CompilationUnit cu = parseResult.getResult().get();

        assertThat(cu.getPackageDeclaration().get().getNameAsString())
                .as("Package should match")
                .isEqualTo("com.example.native_lib");

        final ClassOrInterfaceDeclaration loaderClass =
                cu.getClassByName("native_h_NativeLibraryLoader").get();
        assertThat(loaderClass.isFinal()).as("Loader class should be final").isTrue();

        assertThat(loaderClass.getMethodsByName("expandResourcePath"))
                .as("Should have expandResourcePath")
                .isNotEmpty();
        assertThat(loaderClass.getMethodsByName("detectOsName"))
                .as("Should have detectOsName")
                .isNotEmpty();
        assertThat(loaderClass.getMethodsByName("detectOsArch"))
                .as("Should have detectOsArch")
                .isNotEmpty();
        assertThat(loaderClass.getMethodsByName("extractLibrary"))
                .as("Should have extractLibrary")
                .isNotEmpty();

        final String generatedCode = Files.readString(loaderFile);
        assertThat(generatedCode)
                .as("Should contain resource path template")
                .contains("native/{os.name}-{os.arch}/mynative");
    }

    @Test
    void generatesNativeLibraryLoaderWithCustomExtractionDir() throws IOException {
        // Arrange
        final Path headerFile = this.testProjectDir.resolve("mylib.h");
        this.copyResource("native-loading/custom-dir/mylib.h", headerFile);

        final JextractTestUtils.LibraryDefinition lib = JextractTestUtils.LibraryDefinition.builder()
                .name("mylib")
                .headerFile("mylib.h")
                .targetPackage("com.test.mylib")
                .nativeLibraryResourcePath("libs/{os.name}/mylib")
                .nativeLibraryExtractionDir("build/native-libs")
                .build();

        this.writeBuildScript("25-jextract+2-4", lib);
        this.mockJextractTool(this.gradleUserHome, "25-jextract+2-4");

        // Act
        final BuildResult result = this.createRunner(this.gradleUserHome).build();

        // Assert
        assertThat(result.getOutput()).as("Build should succeed").contains("BUILD SUCCESSFUL");

        final Path loaderFile = this.projectDir
                .toPath()
                .resolve("build/generated/sources/jextract/mylib/com/test/mylib/mylib_h_NativeLibraryLoader.java");
        assertThat(loaderFile).as("Loader file should exist").exists();

        final String generatedCode = Files.readString(loaderFile);
        assertThat(generatedCode).as("Should contain custom extraction dir").contains("native-libs");
    }

    @Test
    void generatesNativeLibraryLoaderWithCachingEnabled() throws IOException {
        // Arrange
        final Path headerFile = this.testProjectDir.resolve("cached.h");
        this.copyResource("native-loading/caching/cached.h", headerFile);

        final JextractTestUtils.LibraryDefinition lib = JextractTestUtils.LibraryDefinition.builder()
                .name("cached")
                .headerFile("cached.h")
                .targetPackage("com.cache.test")
                .nativeLibraryResourcePath("native/cached")
                .nativeLibraryEnableCaching(true)
                .build();

        this.writeBuildScript("25-jextract+2-4", lib);
        this.mockJextractTool(this.gradleUserHome, "25-jextract+2-4");

        // Act
        final BuildResult result = this.createRunner(this.gradleUserHome).build();

        // Assert
        assertThat(result.getOutput()).as("Build should succeed").contains("BUILD SUCCESSFUL");

        final Path loaderFile = this.projectDir
                .toPath()
                .resolve("build/generated/sources/jextract/cached/com/cache/test/cached_h_NativeLibraryLoader.java");
        assertThat(loaderFile).as("Loader file should exist").exists();

        final String generatedCode = Files.readString(loaderFile);
        // Verify caching-related methods are present
        assertThat(generatedCode).as("Should contain getCachedLibrary method").contains("getCachedLibrary");
        assertThat(generatedCode).as("Should contain computeHash method").contains("computeHash");
    }

    @Test
    void platformDetectionMethodsGenerateCorrectly() throws IOException {
        // Arrange
        final Path headerFile = this.testProjectDir.resolve("platform.h");
        this.copyResource("native-loading/platform/platform.h", headerFile);

        final JextractTestUtils.LibraryDefinition lib = JextractTestUtils.LibraryDefinition.builder()
                .name("platform")
                .headerFile("platform.h")
                .targetPackage("com.platform")
                .nativeLibraryResourcePath("native/{os.name}-{os.arch}/lib")
                .build();

        this.writeBuildScript("25-jextract+2-4", lib);
        this.mockJextractTool(this.gradleUserHome, "25-jextract+2-4");

        // Act
        this.createRunner(this.gradleUserHome).build();

        // Assert
        final Path loaderFile = this.projectDir
                .toPath()
                .resolve("build/generated/sources/jextract/platform/com/platform/platform_h_NativeLibraryLoader.java");
        assertThat(loaderFile).as("Loader file should exist").exists();

        final String generatedCode = Files.readString(loaderFile);

        // Verify OS detection logic
        assertThat(generatedCode).as("Should contain windows OS").contains("windows");
        assertThat(generatedCode).as("Should contain macos OS").contains("macos");
        assertThat(generatedCode).as("Should contain linux OS").contains("linux");

        // Verify architecture detection
        assertThat(generatedCode).as("Should contain amd64 architecture").contains("amd64");
        assertThat(generatedCode).as("Should contain aarch64 architecture").contains("aarch64");

        // Verify library file name construction
        assertThat(generatedCode).as("Should contain .dll extension").contains(".dll");
        assertThat(generatedCode).as("Should contain .dylib extension").contains(".dylib");
        assertThat(generatedCode).as("Should contain .so extension").contains(".so");
        assertThat(generatedCode).as("Should add lib prefix").contains("lib\" + fileName");
    }

    @Test
    void injectsLoaderCallIntoHeaderClass() throws IOException {
        // Arrange
        final Path headerFile = this.testProjectDir.resolve("injected.h");
        this.copyResource("native-loading/injected/injected.h", headerFile);

        final JextractTestUtils.LibraryDefinition lib = JextractTestUtils.LibraryDefinition.builder()
                .name("injected")
                .headerFile("injected.h")
                .targetPackage("com.test.injected")
                .nativeLibraryResourcePath("native/lib")
                .headerClassName("InjectedHeader")
                .build();

        this.writeBuildScript("25-jextract+2-4", lib);

        // Use shared mockSmartJextractTool
        this.mockSmartJextractTool(this.gradleUserHome, "25-jextract+2-4", "com.test.injected", "InjectedHeader", false);

        // Act
        final BuildResult result = this.createRunner(this.gradleUserHome).build();

        // Assert
        assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");

        final Path headerClassFile = this.projectDir
                .toPath()
                .resolve("build/generated/sources/jextract/injected/com/test/injected/InjectedHeader.java");

        assertThat(headerClassFile).as("Header class should exist").exists();

        final String headerContent = Files.readString(headerClassFile);
        assertThat(headerContent)
                .as("Should contain loader call")
                .contains("InjectedHeader_NativeLibraryLoader.load();");
    }
}
