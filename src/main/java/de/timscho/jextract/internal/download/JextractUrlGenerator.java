package de.timscho.jextract.internal.download;

import de.timscho.jextract.internal.model.SupportedPlatform;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.Contract;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class JextractUrlGenerator {
    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)-jextract\\+(\\d+)(?:-.*)?$");
    private static final String URL_TEMPLATE =
            "https://download.java.net/java/early_access/jextract/%s/%s/openjdk-%s_%s_bin.tar.gz";

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
