/**
 * The {@code jextract} block a build script writes, and the per-library declarations inside it.
 *
 * <p>Nothing here runs jextract. Every property is a Gradle {@code Property} or {@code Provider}
 * that the plugin copies onto a task at configuration time, so a value set lazily is still read
 * before the task executes. The task holds the same properties again under
 * {@link de.timscho.jextract.task}, documented there in terms of the command line they reach.
 *
 * <p>The same properties are laid out as one table with their defaults in
 * {@code docs/CONFIGURATION.md}, and the resource layout a bundled library needs is in
 * {@code docs/NATIVE_LIBRARY_LOADING.md}. Neither is generated from these comments, so a property
 * added here is added there by hand.
 */
@org.jspecify.annotations.NullMarked
package de.timscho.jextract.extension;
