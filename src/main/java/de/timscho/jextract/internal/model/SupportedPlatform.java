package de.timscho.jextract.internal.model;

import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.gradle.api.GradleException;
import org.jetbrains.annotations.Contract;

/**
 * A platform jextract publishes an archive for, paired with the id naming that archive.
 *
 * <p>No fallback to a neighbouring platform: the nearest archive downloads happily and then cannot
 * execute.
 */
@RequiredArgsConstructor
@Getter
public enum SupportedPlatform {
    /** Windows, whatever the architecture reports, since jextract publishes no ARM64 Windows build. */
    WINDOWS_X64(PlatformType.WINDOWS, "windows-x64"),

    /** Linux on x86-64. */
    LINUX_X64(PlatformType.LINUX, "linux-x64"),

    /** Linux where {@code os.arch} reports {@code aarch64} or {@code arm64}. */
    LINUX_ARM64(PlatformType.LINUX, "linux-aarch64"),

    /** macOS on Intel. */
    MACOS_X64(PlatformType.MACOS, "macos-x64"),

    /** macOS on Apple silicon. */
    MACOS_ARM64(PlatformType.MACOS, "macos-aarch64");

    /** {@return the family deciding which binary name to look for inside the archive} */
    private final PlatformType platformType;

    /** {@return the archive id the download URL is built from, for example {@code linux-aarch64}} */
    private final String id;

    /**
     * {@return the platform matching this JVM's {@code os.name} and {@code os.arch}}
     *
     * @throws GradleException if the pair matches none of the five
     */
    @Contract(pure = true)
    public static SupportedPlatform getCurrentSupported() {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        final String arch = System.getProperty("os.arch").toLowerCase(Locale.ENGLISH);
        final boolean isArm = arch.contains("aarch64") || arch.contains("arm64");

        if (os.contains("win")) {
            return SupportedPlatform.WINDOWS_X64;
        }
        if (os.contains("mac")) {
            return isArm ? SupportedPlatform.MACOS_ARM64 : SupportedPlatform.MACOS_X64;
        }
        if (os.contains("nux")) {
            return isArm ? SupportedPlatform.LINUX_ARM64 : SupportedPlatform.LINUX_X64;
        }

        throw new GradleException("Unsupported OS/Arch combination: " + os + " / " + arch);
    }
}
