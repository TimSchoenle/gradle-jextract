/**
 * Functional tests, run through Gradle TestKit against generated build scripts.
 *
 * <p>Most of them put a fake jextract into a temporary Gradle user home, because asserting on the
 * argument list the plugin builds needs no real tool and no network. {@code JextractRealDownloadTest}
 * is the exception and does reach {@code download.java.net}.
 */
@org.jspecify.annotations.NullMarked
package de.timscho.jextract;
