/**
 * The {@code jextract} block a build script writes, and the per-library declarations inside it.
 *
 * <p>Nothing here runs jextract. Every property is a Gradle {@code Property} or {@code Provider}
 * that the plugin copies onto a task at configuration time, so a value set lazily is still read
 * before the task executes. The task holds the same properties again under
 * {@link de.timscho.jextract.task}, documented there in terms of the command line they reach.
 */
@org.jspecify.annotations.NullMarked
package de.timscho.jextract.extension;
