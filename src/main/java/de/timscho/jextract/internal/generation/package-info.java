/**
 * Emitting the runtime loader and wiring it into the classes jextract produced.
 *
 * <p>Both classes here go through JavaParser rather than string templates. For the emitted loader
 * that is a convenience; for the edit to jextract's own output it is the requirement, because the
 * initializer has to be added as a member of the right class and the check for one already being
 * there has to look at static initializers only.
 *
 * <p>The loader is generated for one configuration and holds no branch for another. Caching, the
 * extraction directory and the resource template are all resolved at build time, so changing any of
 * them means regenerating rather than passing a flag.
 */
@org.jspecify.annotations.NullMarked
package de.timscho.jextract.internal.generation;
