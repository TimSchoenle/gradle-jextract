/**
 * Downloads a pinned jextract build and runs it over a C header to produce Java FFM bindings.
 *
 * <p>{@link de.timscho.jextract.extension} and {@link de.timscho.jextract.task} are the surface a
 * build script reaches. Everything under {@code de.timscho.jextract.internal} carries no
 * compatibility promise and is excluded from the published Javadoc.
 *
 * <p>jextract is published as a per-platform archive on {@code download.java.net} and on no Maven
 * repository, so it is fetched by a shared build service instead of being resolved as a dependency.
 * That is also why the version is a project-wide setting rather than a per-library one: six library
 * declarations reach one service and cost one download.
 *
 * <p>jextract itself emits bindings and leaves loading the native library to whoever calls them.
 * The two mechanisms here that close that gap are mutually exclusive per library: a system library
 * named on the jextract command line, or a file extracted out of the JAR at first use. A
 * declaration setting both fails the task instead of picking one, because the choice decides where
 * the library comes from on the machine that runs the bindings and neither answer is safe to
 * assume.
 */
@org.jspecify.annotations.NullMarked
package de.timscho.jextract;
