package de.timscho.jextract;

import de.timscho.jextract.internal.download.JextractToolService;
import de.timscho.jextract.internal.model.PlatformType;
import de.timscho.jextract.internal.model.SupportedPlatform;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

public abstract class AbstractJextractFunctionalTest {
    @TempDir
    protected Path testProjectDir;

    protected File projectDir;
    protected File buildFile;
    protected File settingsFile;

    @BeforeEach
    void setup() throws IOException {
        this.projectDir = this.testProjectDir.toFile();
        this.buildFile = new File(this.projectDir, "build.gradle");
        this.settingsFile = new File(this.projectDir, "settings.gradle");
        Files.writeString(this.settingsFile.toPath(), "rootProject.name = 'jextract-demo'");
    }

    protected Path getCacheDir(final Path userHome, final String version) {
        final String folderName = version.replaceAll("[^a-zA-Z0-9.-]", "_");
        return userHome.resolve(JextractPlugin.RELATIVE_TOOL_CACHE).resolve(folderName);
    }

    private File createJextractFileMock(final Path userHome, final String version) throws IOException {
        final Path cacheDir = this.getCacheDir(userHome, version);
        final Path binDir = cacheDir.resolve("bin");
        Files.createDirectories(binDir);

        final boolean isWindows = SupportedPlatform.getCurrentSupported().getPlatformType() == PlatformType.WINDOWS;
        final String scriptName = isWindows ? "jextract.bat" : "jextract";
        return binDir.resolve(scriptName).toFile();
    }

    protected void mockJextractTool(final Path userHome, final String version) throws IOException {
        final File jextractFile = this.createJextractFileMock(userHome, version);

        // Simple debug log file in the test project dir
        final Path debugLog = this.testProjectDir.resolve("jextract-mock.log");

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(jextractFile.toPath()))) {
            if (SupportedPlatform.getCurrentSupported().getPlatformType() == PlatformType.WINDOWS) {
                writer.println("@echo off");
                writer.println("echo Mock Jextract Running...");
                writer.println("echo Args: %* >> \"" + debugLog.toAbsolutePath() + "\"");

                // Parse arguments based on fixed order from JextractTask
                // %1=--output, %2=dir, %3=--target-package, %4=pkg, %5=--header-class-name, %6=class

                writer.println("set OUTDIR=%~2");
                writer.println("set PKG=%~4");
                writer.println("set CLASS=%~6");

                // Replace dots with backslashes for package path
                writer.println("set PKG_PATH=%PKG:.=\\%");

                writer.println("mkdir \"%OUTDIR%\\%PKG_PATH%\" 2>nul");
                writer.println("echo // Generated > \"%OUTDIR%\\%PKG_PATH%\\%CLASS%.java\"");
            } else {
                writer.println("#!/bin/sh");
                writer.println("echo 'Mock Jextract Running...'");
                writer.println("echo \"Args: $@\" >> \"" + debugLog.toAbsolutePath() + "\"");

                // $2 is output dir, $4 is package, $6 is class name
                writer.println("OUTDIR=\"$2\"");
                writer.println("PKG=\"$4\"");
                writer.println("CLASS=\"$6\"");

                writer.println("PKG_PATH=$(echo \"$PKG\" | tr . /)");
                writer.println("mkdir -p \"$OUTDIR/$PKG_PATH\"");
                writer.println("touch \"$OUTDIR/$PKG_PATH/$CLASS.java\"");
            }
        }
        jextractFile.setExecutable(true);

        // Create the marker file
        Files.createFile(this.getCacheDir(userHome, version).resolve(JextractToolService.FILE_INTEGRITY_NAME));
    }

    protected GradleRunner createRunner(final Path gradleUserHome) {
        return GradleRunner.create()
                .withProjectDir(this.projectDir)
                .withArguments(
                        "build",
                        "--stacktrace",
                        "--gradle-user-home",
                        gradleUserHome.toAbsolutePath().toString())
                .withPluginClasspath()
                .forwardOutput();
    }

    protected void writeBuildScript(
            @Nullable final String toolVersion, final JextractTestUtils.LibraryDefinition... libraries)
            throws IOException {
        JextractTestUtils.writeBuildScript(this.buildFile.toPath(), toolVersion, libraries);
    }

    protected void copyResource(final String resourcePath, final Path destination) throws IOException {
        try (java.io.InputStream in = this.getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            Files.copy(in, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    protected void deleteDirectory(final Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (java.util.stream.Stream<Path> entries = Files.list(path)) {
                entries.forEach(entry -> {
                    try {
                        this.deleteDirectory(entry);
                    } catch (final IOException e) {
                        // Ignore
                    }
                });
            }
        }
        Files.deleteIfExists(path);
    }

    protected void mockSmartJextractTool(
            final Path userHome,
            final String version,
            final String targetPkg,
            final String headerClassNameOrContent,
            final boolean isContent)
            throws IOException {
        final File executable = this.createJextractFileMock(userHome, version);

        // Prepare content
        String content = headerClassNameOrContent;
        String className = "GeneratedClass"; // Default if content is raw

        if (!isContent) {
            // It's a class name, generate dummy content
            className = headerClassNameOrContent;
            content = "package " + targetPkg + ";\n" + "public class " + className + " {\n"
                    + "    // Generated content\n" + "    public static int add(int a, int b) { return a + b; }\n"
                    + "}";
        } else {
            // Try to extract class name if possible, or use default
            // This is a simple heuristic
            if (content.contains("class ")) {
                final int start = content.indexOf("class ") + 6;
                int end = content.indexOf(' ', start);
                if (end == -1) {
                    end = content.indexOf('{', start);
                }
                if (end != -1) {
                    className = content.substring(start, end).trim();
                }
            }
        }

        final Path parentDir = executable.getParentFile().toPath();
        final Path contentFile = parentDir.resolve("mock_content.java");
        Files.writeString(contentFile, content);

        final boolean isWindows = SupportedPlatform.getCurrentSupported().getPlatformType() == PlatformType.WINDOWS;
        final String pkgPath = targetPkg.replace('.', isWindows ? '\\' : '/');

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(executable.toPath()))) {
            if (isWindows) {
                writer.println("@echo off");
                writer.println("echo Mock Jextract Running...");
                writer.println("mkdir \"%~2\\" + pkgPath + "\"");
                writer.println("copy /Y \"" + contentFile.toAbsolutePath() + "\" \"%~2\\" + pkgPath + "\\" + className
                        + ".java\"");
            } else {
                writer.println("#!/bin/sh");
                writer.println("echo 'Mock Jextract Running...'");
                writer.println("mkdir -p \"$2/" + pkgPath + "\"");
                writer.println(
                        "cp \"" + contentFile.toAbsolutePath() + "\" \"$2/" + pkgPath + "/" + className + ".java\"");
            }
        }
        executable.setExecutable(true);
        Files.createFile(this.getCacheDir(userHome, version).resolve(JextractToolService.FILE_INTEGRITY_NAME));
    }
}
