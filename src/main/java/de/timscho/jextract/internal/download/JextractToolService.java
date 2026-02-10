package de.timscho.jextract.internal.download;

import de.timscho.jextract.internal.model.PlatformType;
import de.timscho.jextract.internal.model.SupportedPlatform;
import de.timscho.jextract.internal.util.GeneratedConstant;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.inject.Inject;
import org.gradle.api.GradleException;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.jspecify.annotations.Nullable;

public abstract class JextractToolService implements BuildService<JextractToolService.Params> {
    public static final String DEFAULT_VERSION = GeneratedConstant.JEXTRACT_VERSION;
    public static final String FILE_INTEGRITY_NAME = ".gradleJextractDownload";
    private static final int HTTP_OK = 200;

    @Inject
    protected abstract FileSystemOperations getFs();

    @Inject
    protected abstract ArchiveOperations getArchives();

    private final HttpClient httpClient =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    /**
     * Resolves and returns the jextract tool executable.
     * Downloads and caches the tool if not already present.
     *
     * @param logger The Gradle logger to use for logging.
     * @return The jextract executable file
     */
    public File getExecutable(final org.gradle.api.logging.Logger logger) {
        final String version = this.getParameters().getVersion().getOrElse(JextractToolService.DEFAULT_VERSION);
        final String folderName = version.replaceAll("[^a-zA-Z0-9.-]", "_");
        final Path toolDir = this.resolveToolDir(folderName, logger);

        // Determine the correct binary name for the OS
        final boolean isWindows = SupportedPlatform.getCurrentSupported().getPlatformType() == PlatformType.WINDOWS;
        final String binaryName = isWindows ? "jextract.bat" : "jextract";
        final Path relativePathWithBin = Path.of("bin", binaryName);

        // Check direct path: toolDir/bin/<binary>
        final File bin = toolDir.resolve(relativePathWithBin).toFile();
        if (bin.exists()) {
            logger.debug("Found jextract binary at: {}", bin);
            return bin;
        }

        // Deep search (handle nested extraction like 'jextract-25/bin/...')
        final @Nullable File[] subDirs = toolDir.toFile().listFiles(File::isDirectory);
        if (subDirs != null) {
            for (final File sub : subDirs) {
                if (sub == null) {
                    continue;
                }

                final File nestedBin = sub.toPath().resolve(relativePathWithBin).toFile();
                if (nestedBin.exists()) {
                    logger.debug("Found jextract binary (nested) at: {}", nestedBin);
                    return nestedBin;
                }
            }
        }

        throw new GradleException("Jextract binary '" + binaryName + "' not found in " + toolDir);
    }

    private Path resolveToolDir(final String folderName, final org.gradle.api.logging.Logger logger) {
        synchronized (this) {
            final Directory cacheBase = this.getParameters().getCacheDir().get();
            final Directory versionDir = cacheBase.dir(folderName);
            final RegularFile marker = versionDir.file(JextractToolService.FILE_INTEGRITY_NAME);

            if (versionDir.getAsFile().exists() && marker.getAsFile().exists()) {
                logger.debug("Using cached jextract from: {}", versionDir);
                return versionDir.getAsFile().toPath();
            }

            final SupportedPlatform platform = SupportedPlatform.getCurrentSupported();
            final String version = this.getParameters().getVersion().getOrElse(JextractToolService.DEFAULT_VERSION);
            final String url = JextractUrlGenerator.generateUrl(version, platform);

            logger.lifecycle("Downloading jextract ({}) from: {}", folderName, url);

            try {
                // Clean partial downloads
                if (versionDir.getAsFile().exists()) {
                    this.getFs().delete(s -> s.delete(versionDir));
                }
                Files.createDirectories(versionDir.getAsFile().toPath());

                this.downloadAndExtract(url, versionDir.getAsFile());

                // Mark success
                marker.getAsFile().createNewFile();
            } catch (final Exception exception) {
                throw new GradleException("Failed to download jextract from " + url, exception);
            }

            return versionDir.getAsFile().toPath();
        }
    }

    private void downloadAndExtract(final String url, final File targetDir) throws IOException, InterruptedException {
        final File tempArchive = File.createTempFile("jextract", ".tar.gz");
        try {
            final HttpRequest request =
                    HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            final HttpResponse<Path> response =
                    this.httpClient.send(request, HttpResponse.BodyHandlers.ofFile(tempArchive.toPath()));

            if (response.statusCode() != JextractToolService.HTTP_OK) {
                throw new IOException("Download request failed with status code: " + response.statusCode());
            }

            this.getFs().sync(spec -> {
                spec.from(this.getArchives().tarTree(this.getArchives().gzip(tempArchive)));
                spec.into(targetDir);
            });
        } finally {
            tempArchive.delete();
        }
    }

    public interface Params extends BuildServiceParameters {
        Property<String> getVersion();

        DirectoryProperty getCacheDir();
    }
}
