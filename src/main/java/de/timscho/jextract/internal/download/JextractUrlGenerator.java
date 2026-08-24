package de.timscho.jextract.internal.download;

import de.timscho.jextract.internal.model.SupportedPlatform;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Contract;

/** Builds the {@code download.java.net} URL for one jextract build on one platform. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class JextractUrlGenerator {
    // The two capture groups are the major and the build number, which appear as their own path
    // segments in the URL. That is why the version is parsed here rather than passed through.
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)-jextract\\+(\\d+)(?:-.*)?$");

    private static final String URL_TEMPLATE =
            "https://download.java.net/java/early_access/jextract/%s/%s/openjdk-%s_%s_bin.tar.gz";

    /**
     * {@return the archive URL for {@code version} on {@code platform}}
     *
     * <p>Rejects a version the URL cannot be assembled from, which is what turns a typo into a
     * failure before any network call rather than a 404 halfway through a build.
     *
     * @param version a build id of the form {@code <major>-jextract+<build>}, optionally suffixed
     * @param platform decides the {@code _bin} archive variant
     * @throws IllegalArgumentException if the version does not match that form
     */
    @Contract(pure = true)
    static String generateUrl(final String version, final SupportedPlatform platform) {
        final Matcher matcher = JextractUrlGenerator.VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Version string '" + version + "' does not match expected pattern: "
                    + JextractUrlGenerator.VERSION_PATTERN.pattern());
        }

        final String major = matcher.group(1);
        final String build = matcher.group(2);

        return String.format(JextractUrlGenerator.URL_TEMPLATE, major, build, version, platform.getId());
    }
}
