package de.timscho.jextract.internal.model;

/** Operating system family of the machine running Gradle. */
public enum PlatformType {
    /** Windows, where the launcher inside the archive is {@code jextract.bat} and not {@code jextract}. */
    WINDOWS,

    /** Linux, on x86-64 or aarch64. */
    LINUX,

    /** macOS, on Intel or Apple silicon. */
    MACOS
}
