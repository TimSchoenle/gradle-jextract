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

/**
 * Supplies the jextract executable, downloading and unpacking it the first time one is asked for.
 *
 * <p>One instance is shared by every task in the build, and resolution runs under a lock.
 */
public abstract class JextractToolService implements BuildService<JextractToolService.Params> {
    /** The jextract build this release was compiled against, read from {@code gradle/jextract-version}. */
    public static final String DEFAULT_VERSION = GeneratedConstant.JEXTRACT_VERSION;

    /**
     * Marker written into a version directory once extraction has finished, and the only thing that
     * makes that directory count as cached.
     */
    public static final String FILE_INTEGRITY_NAME = ".gradleJextractDownload";

    private static final int HTTP_OK = 200;

    /** {@return the file operations used to unpack the archive and to clear a partial download} */
    @Inject
    protected abstract FileSystemOperations getFs();

    /** {@return the archive operations the downloaded {@code .tar.gz} is read through} */
    @Inject
    protected abstract ArchiveOperations getArchives();

    private final HttpClient httpClient =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    // No constructor is declared, deliberately. Gradle generates the concrete subclass of a build
    // service and its constructor selector then demands an @Inject constructor it cannot find,
    // so `Failed to create service 'jextractTool'` is what a declared constructor here buys.

    /**
     * {@return the jextract binary for this machine, fetched and unpacked on a cache miss}
     *
     * @param logger receives the download line at lifecycle level and the resolved path at debug
     * @throws GradleException if the download or extraction fails, or if the extracted tree holds
     *     no {@code bin/jextract} at either depth searched
     */
    public File getExecutable(final org.gradle.api.logging.Logger logger) {
        final String version = this.getParameters().getVersion().getOrElse(JextractToolService.DEFAULT_VERSION);
        // A version string carries a '+', which the download URL accepts and Windows paths do not.
        final String folderName = version.replaceAll("[^a-zA-Z0-9.-]", "_");
        final Path toolDir = this.resolveToolDir(folderName, logger);

        final boolean isWindows = SupportedPlatform.getCurrentSupported().getPlatformType() == PlatformType.WINDOWS;
        final String binaryName = isWindows ? "jextract.bat" : "jextract";
        final Path relativePathWithBin = Path.of("bin", binaryName);

        final File bin = toolDir.resolve(relativePathWithBin).toFile();
        if (bin.exists()) {
            logger.debug("Found jextract binary at: {}", bin);
            return bin;
        }

        // One level only: the nesting seen so far is a single 'jextract-<major>' directory.
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

    // Synchronized on the service, which Gradle shares across the build, so parallel tasks reaching
    // a cold cache queue behind one download instead of racing to write the same directory.
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
                // Anything already there failed to finish, or the marker would have been found.
                if (versionDir.getAsFile().exists()) {
                    this.getFs().delete(s -> s.delete(versionDir));
                }
                Files.createDirectories(versionDir.getAsFile().toPath());

                this.downloadAndExtract(url, versionDir.getAsFile());

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

    /** What the service is registered with, fixed for the life of the build. */
    public interface Params extends BuildServiceParameters {
        /** {@return the jextract version to fetch, taken from the extension at registration time} */
        Property<String> getVersion();

        /**
         * {@return the directory holding one subdirectory per version}
         *
         * <p>Under the Gradle user home rather than the build directory, so the tool survives a
         * {@code clean} and is shared between projects on the machine.
         */
        DirectoryProperty getCacheDir();
    }
}
