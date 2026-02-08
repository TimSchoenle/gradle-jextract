package de.timscho.jextract.internal.download;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.timscho.jextract.internal.model.SupportedPlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JextractUrlGeneratorTest {

    @ParameterizedTest
    @CsvSource({
        "25-jextract+2-4, LINUX_X64, https://download.java.net/java/early_access/jextract/25/2/openjdk-25-jextract+2-4_linux-x64_bin.tar.gz",
        "25-jextract+2-4, MACOS_ARM64, https://download.java.net/java/early_access/jextract/25/2/openjdk-25-jextract+2-4_macos-aarch64_bin.tar.gz",
        "26-jextract+5-1, WINDOWS_X64, https://download.java.net/java/early_access/jextract/26/5/openjdk-26-jextract+5-1_windows-x64_bin.tar.gz"
    })
    void generatesUrlCorrectly(String version, SupportedPlatform platform, String expectedUrl) {
        // Act
        final String url = JextractUrlGenerator.generateUrl(version, platform);

        // Assert
        assertThat(url).isEqualTo(expectedUrl);
    }

    @Test
    void throwsOnInvalidVersionFormat() {
        // Arrange
        final String invalidVersion = "invalid-version";

        // Act & Assert
        assertThatThrownBy(() -> JextractUrlGenerator.generateUrl(invalidVersion, SupportedPlatform.LINUX_X64))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
