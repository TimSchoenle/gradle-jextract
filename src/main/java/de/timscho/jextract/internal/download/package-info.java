/**
 * Getting the jextract tool onto the machine and finding its binary afterwards.
 *
 * <p>The archive layout is not stable across jextract builds, which is why resolution searches two
 * depths instead of trusting one path. Nothing here parses headers or writes Java.
 */
@org.jspecify.annotations.NullMarked
package de.timscho.jextract.internal.download;
