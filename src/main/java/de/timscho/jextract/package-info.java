/**
 * Downloads a pinned jextract build and runs it over a C header to produce Java FFM bindings.
 *
 * <p>{@link de.timscho.jextract.extension} and {@link de.timscho.jextract.task} are the surface a
 * build script reaches. Everything under {@code de.timscho.jextract.internal} carries no
 * compatibility promise and is excluded from the published Javadoc.
 *
 * <p>jextract is published as a per-platform archive on {@code download.java.net} and on no Maven
 * repository, so it is fetched by a shared build service instead of being resolved as a dependency.
 * The version is a project-wide setting rather than a per-library one, which is what makes six
 * library declarations cost one download. A version directory in the cache counts as usable only
 * once the marker file written after extraction is present; without it the directory is deleted and
 * fetched again, because a download interrupted halfway otherwise leaves a tree that looks cached
 * and holds no binary.
 *
 * <p>jextract itself emits bindings and leaves loading the native library to whoever calls them.
 * The two mechanisms here that close that gap are mutually exclusive per library: a system library
 * named on the jextract command line, or a file extracted out of the JAR at first use. A
 * declaration setting both fails the task instead of picking one, because the choice decides where
 * the library comes from on the machine that runs the bindings and neither answer is safe to
 * assume.
 *
 * <p>The loader is reached through a static initializer added to the class jextract wrote. That
 * edit is made by parsing the file and adding a node, not by appending text, so the second run over
 * an existing generated tree finds the initializer already in the syntax tree and does nothing.
 */
@org.jspecify.annotations.NullMarked
package de.timscho.jextract;
