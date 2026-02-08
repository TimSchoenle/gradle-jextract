package de.timscho.jextract.internal.model;

import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.gradle.api.GradleException;
import org.jetbrains.annotations.Contract;

@RequiredArgsConstructor
@Getter
public enum SupportedPlatform {
    WINDOWS_X64(PlatformType.WINDOWS, "windows-x64"),
    LINUX_X64(PlatformType.LINUX, "linux-x64"),
    LINUX_ARM64(PlatformType.LINUX, "linux-aarch64"),
    MACOS_X64(PlatformType.MACOS, "macos-x64"),
    MACOS_ARM64(PlatformType.MACOS, "macos-aarch64");

    private final PlatformType platformType;
    private final String id;

    /**
     * Detects and returns the current supported platform based on system properties.
     *
     * @return The current SupportedPlatform.
     * @throws GradleException if the OS or architecture is not supported.
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
