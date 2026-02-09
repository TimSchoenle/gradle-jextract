package de.timscho.jextract.internal.generation;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.File;
import java.io.IOException;
import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.Contract;

@Builder
@Value
public class NativeLibraryLoaderInjector {
    File target;
    String headerClass;
    String loaderClassName;
    String staticLoaderMethodName;
    org.gradle.api.logging.Logger logger;

    @Contract(pure = true)
    private boolean isAlreadyInjected(final ClassOrInterfaceDeclaration customClass) {
        return customClass.getMembers().stream()
                .filter(member -> member instanceof com.github.javaparser.ast.body.InitializerDeclaration)
                .map(member -> (com.github.javaparser.ast.body.InitializerDeclaration) member)
                .filter(com.github.javaparser.ast.body.InitializerDeclaration::isStatic)
                .anyMatch(init -> init.getBody()
                        .toString()
                        .contains(this.loaderClassName + "." + this.staticLoaderMethodName + "()"));
    }

    @Contract(pure = true)
    private BlockStmt createLoaderInnitBlock() {
        final BlockStmt block = new BlockStmt();
        final TryStmt tryStmt = new TryStmt();
        final BlockStmt tryBlock = new BlockStmt();
        tryBlock.addStatement(new MethodCallExpr(new NameExpr(this.loaderClassName), this.staticLoaderMethodName));
        tryStmt.setTryBlock(tryBlock);

        final CatchClause catchClause = new CatchClause();
        catchClause.setParameter(new Parameter(new ClassOrInterfaceType(null, "Exception"), "exception"));
        final BlockStmt catchBlock = new BlockStmt();
        catchBlock.addStatement(new ThrowStmt(new ObjectCreationExpr(
                null, new ClassOrInterfaceType(null, "RuntimeException"), new NodeList<>(new NameExpr("exception")))));
        catchClause.setBody(catchBlock);
        tryStmt.setCatchClauses(new NodeList<>(catchClause));
        block.addStatement(tryStmt);

        return block;
    }

    /**
     * Inject the NativeLibraryLoader.load() method into the given class.
     *
     * @throws IOException For file I/O errors
     */
    public void inject() throws IOException {
        final CompilationUnit compilationUnit = StaticJavaParser.parse(this.target);
        final ClassOrInterfaceDeclaration customClass =
                compilationUnit.getClassByName(this.headerClass).orElse(null);

        if (customClass == null) {
            this.logger.warn("Could not find class {} in {}", this.headerClass, this.target);
            return;
        }

        if (this.isAlreadyInjected(customClass)) {
            this.logger.info("Loader is already injected in {}, skipping", this.target);
            return;
        }

        final BlockStmt loaderInnitBlock = this.createLoaderInnitBlock();
        customClass.addMember(new InitializerDeclaration(true, loaderInnitBlock));

        java.nio.file.Files.writeString(this.target.toPath(), compilationUnit.toString());
        this.logger.info("Injected NativeLibraryLoader.load() into {}", this.target);
    }
}
