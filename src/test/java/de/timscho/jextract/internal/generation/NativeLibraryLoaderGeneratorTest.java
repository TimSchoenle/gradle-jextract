package de.timscho.jextract.internal.generation;

import static org.assertj.core.api.Assertions.assertThat;

import de.timscho.jextract.extension.NativeLibraryLoadingConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeLibraryLoaderGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesBasicLoaderClass() throws IOException {
        // Arrange
        final ObjectFactory objectFactory = ProjectBuilder.builder().build().getObjects();
        final NativeLibraryLoadingConfig config = objectFactory.newInstance(NativeLibraryLoadingConfig.class);
        config.getResourcePath().set("native/{os.name}-{os.arch}/mylib");

        final Logger logger = Logging.getLogger(NativeLibraryLoaderGeneratorTest.class);
        final NativeLibraryLoaderGenerator generator =
                new NativeLibraryLoaderGenerator("com.example", "MyLib", config, this.tempDir, logger);

        // Act
        generator.generate();

        // Assert
        final Path loaderFile = this.tempDir.resolve("com/example/MyLib_NativeLibraryLoader.java");
        assertThat(loaderFile).as("Loader file should be generated").exists();

        final String content = Files.readString(loaderFile);
        assertThat(content).as("Should have correct package").contains("package com.example");
        assertThat(content).as("Should have loader class").contains("class MyLib_NativeLibraryLoader");
        assertThat(content).as("Should have OS detection").contains("detectOsName");
        assertThat(content).as("Should have arch detection").contains("detectOsArch");
        assertThat(content).as("Should have resource path template").contains("native/{os.name}-{os.arch}/mylib");
    }

    @Test
    void generatesPlatformDetectionMethods() throws IOException {
        // Arrange
        final ObjectFactory objectFactory = ProjectBuilder.builder().build().getObjects();
        final NativeLibraryLoadingConfig config = objectFactory.newInstance(NativeLibraryLoadingConfig.class);
        config.getResourcePath().set("libs/native");

        final Logger logger = Logging.getLogger(NativeLibraryLoaderGeneratorTest.class);
        final NativeLibraryLoaderGenerator generator =
                new NativeLibraryLoaderGenerator("test.platform", "PlatformLib", config, this.tempDir, logger);

        // Act
        generator.generate();

        // Assert
        final String content =
                Files.readString(this.tempDir.resolve("test/platform/PlatformLib_NativeLibraryLoader.java"));

        // Check OS detection
        assertThat(content).as("Should detect Windows").contains("\"windows\"");
        assertThat(content).as("Should detect macOS").contains("\"macos\"");
        assertThat(content).as("Should detect Linux").contains("\"linux\"");

        // Check architecture detection
        assertThat(content).as("Should detect amd64").contains("\"amd64\"");
        assertThat(content).as("Should detect aarch64").contains("\"aarch64\"");

        // Check library file naming
        assertThat(content).as("Should handle Windows DLL").contains(".dll");
        assertThat(content).as("Should handle macOS dylib").contains(".dylib");
        assertThat(content).as("Should handle Linux SO").contains(".so");
    }

    @Test
    void generatesWithCustomExtractionDir() throws IOException {
        // Arrange
        final ObjectFactory objectFactory = ProjectBuilder.builder().build().getObjects();
        final NativeLibraryLoadingConfig config = objectFactory.newInstance(NativeLibraryLoadingConfig.class);
        config.getResourcePath().set("native/lib");
        config.getExtractionDir().set(this.tempDir.resolve("custom-extract").toFile());

        final Logger logger = Logging.getLogger(NativeLibraryLoaderGeneratorTest.class);
        final NativeLibraryLoaderGenerator generator =
                new NativeLibraryLoaderGenerator("com.custom", "CustomLib", config, this.tempDir, logger);

        // Act
        generator.generate();

        // Assert
        final String content = Files.readString(this.tempDir.resolve("com/custom/CustomLib_NativeLibraryLoader.java"));
        assertThat(content)
                .as("Should reference extraction directory")
                .satisfiesAnyOf(c -> assertThat(c).contains("custom-extract"), c -> assertThat(c)
                        .contains("getExtractionDirectory"));
    }

    @Test
    void generatesWithCachingEnabled() throws IOException {
        // Arrange
        final ObjectFactory objectFactory = ProjectBuilder.builder().build().getObjects();
        final NativeLibraryLoadingConfig config = objectFactory.newInstance(NativeLibraryLoadingConfig.class);
        config.getResourcePath().set("native/cached");
        config.getEnableCaching().set(true);

        final Logger logger = Logging.getLogger(NativeLibraryLoaderGeneratorTest.class);
        final NativeLibraryLoaderGenerator generator =
                new NativeLibraryLoaderGenerator("com.cache", "CacheLib", config, this.tempDir, logger);

        // Act
        generator.generate();

        // Assert
        final String content = Files.readString(this.tempDir.resolve("com/cache/CacheLib_NativeLibraryLoader.java"));
        assertThat(content).as("Should have caching method").contains("getCachedLibrary");
        assertThat(content).as("Should have hash computation").contains("computeHash");
        assertThat(content).as("Should use SHA-256 for hashing").contains("SHA-256");
    }

    @Test
    void generatesWithoutCaching() throws IOException {
        // Arrange
        final ObjectFactory objectFactory = ProjectBuilder.builder().build().getObjects();
        final NativeLibraryLoadingConfig config = objectFactory.newInstance(NativeLibraryLoadingConfig.class);
        config.getResourcePath().set("native/lib");
        config.getEnableCaching().set(false);

        final Logger logger = Logging.getLogger(NativeLibraryLoaderGeneratorTest.class);
        final NativeLibraryLoaderGenerator generator =
                new NativeLibraryLoaderGenerator("com.nocache", "NoCacheLib", config, this.tempDir, logger);

        // Act
        generator.generate();

        // Assert
        final String content =
                Files.readString(this.tempDir.resolve("com/nocache/NoCacheLib_NativeLibraryLoader.java"));
        assertThat(content).as("Should not have caching method").doesNotContain("getCachedLibrary");
        assertThat(content).as("Should not have hash computation").doesNotContain("computeHash");
    }
}
