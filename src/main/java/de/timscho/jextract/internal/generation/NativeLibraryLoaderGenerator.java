package de.timscho.jextract.internal.generation;

import static com.github.javaparser.StaticJavaParser.parseExpression;
import static com.github.javaparser.StaticJavaParser.parseStatement;
import static com.github.javaparser.StaticJavaParser.parseType;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import de.timscho.jextract.extension.NativeLibraryLoadingConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Builder;
import org.jetbrains.annotations.Contract;

@Builder
public final class NativeLibraryLoaderGenerator {
    private static final String LOADER_METHOD_NAME = "load";

    private final String targetPackage;
    private final String headerClassName;
    private final NativeLibraryLoadingConfig config;
    private final Path outputDirectory;
    private final org.gradle.api.logging.Logger logger;

    @Contract(pure = true)
    private String getFinalClassName() {
        return this.headerClassName + "_NativeLibraryLoader";
    }

    public void injectLoader() throws IOException {
        final Path targetFile = this.createPackageDirectory().resolve(this.headerClassName + ".java");
        if (!Files.exists(targetFile)) {
            throw new IllegalStateException("Header class not found: " + targetFile);
        }

        final NativeLibraryLoaderInjector injector = NativeLibraryLoaderInjector.builder()
                .target(targetFile.toFile())
                .headerClass(this.headerClassName)
                .loaderClassName(this.getFinalClassName())
                .staticLoaderMethodName(NativeLibraryLoaderGenerator.LOADER_METHOD_NAME)
                .logger(this.logger)
                .build();
        injector.inject();
    }

    /**
     * Generates the NativeLibraryLoader.java file in the output directory.
     */
    public void generate() throws IOException {
        final String className = this.getFinalClassName();

        final CompilationUnit cu = new CompilationUnit();
        cu.setPackageDeclaration(this.targetPackage);

        final ClassOrInterfaceDeclaration loaderClass = cu.addClass(className)
                .setModifiers(Modifier.Keyword.PUBLIC, Modifier.Keyword.FINAL)
                .setJavadocComment("""
                        Auto-generated loader for platform-specific native libraries."
                        "This class extracts and loads native libraries from JAR resources.
                        """);

        final FieldDeclaration loadedField = loaderClass.addField(
                PrimitiveType.booleanType(), "loaded", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
        loadedField.getVariable(0).setInitializer(new BooleanLiteralExpr(false));

        // Static initializer
        final TryStmt tryStmt = new TryStmt();
        final BlockStmt tryBody = new BlockStmt();
        tryBody.addStatement(new MethodCallExpr(NativeLibraryLoaderGenerator.LOADER_METHOD_NAME));
        tryStmt.setTryBlock(tryBody);

        final CatchClause catchClause = new CatchClause();
        catchClause.setParameter(new Parameter(parseType("Exception"), "exception"));
        final BlockStmt catchBody = new BlockStmt();
        catchBody.addStatement(new ThrowStmt(new ObjectCreationExpr(
                null,
                new ClassOrInterfaceType(null, "RuntimeException"),
                new NodeList<>(new StringLiteralExpr("Failed to load native library"), new NameExpr("exception")))));
        catchClause.setBody(catchBody);
        tryStmt.setCatchClauses(new NodeList<>(catchClause));

        final BlockStmt staticBlock = new BlockStmt();
        staticBlock.addStatement(tryStmt);
        loaderClass.addMember(new InitializerDeclaration(true, staticBlock));

        // Private constructor
        final ConstructorDeclaration constructor = loaderClass.addConstructor(Modifier.Keyword.PRIVATE);
        constructor
                .getBody()
                .addStatement(new ThrowStmt(new ObjectCreationExpr(
                        null,
                        new ClassOrInterfaceType(null, "UnsupportedOperationException"),
                        new NodeList<>(new StringLiteralExpr("Utility class")))));

        this.addMethods(loaderClass);

        // Create package directory structure
        final Path packagePath = this.createPackageDirectory();

        // Write the Java file
        final Path outputFile = packagePath.resolve(className + ".java");
        Files.writeString(outputFile, cu.toString());

        this.logger.info("Generated NativeLibraryLoader at: {}", outputFile);
    }

    private void addMethods(final ClassOrInterfaceDeclaration loaderClass) {
        // Main load method
        final MethodDeclaration loadMethod = loaderClass.addMethod(
                NativeLibraryLoaderGenerator.LOADER_METHOD_NAME,
                Modifier.Keyword.PUBLIC,
                Modifier.Keyword.STATIC,
                Modifier.Keyword.SYNCHRONIZED);
        loadMethod.addThrownException(IOException.class);
        final BlockStmt loadBody = loadMethod.getBody().get();

        loadBody.addStatement(new IfStmt(new NameExpr("loaded"), new ReturnStmt(), null));

        final VariableDeclarator resourcePathVar = new VariableDeclarator(
                parseType("String"),
                "resourcePath",
                new MethodCallExpr(
                        "expandResourcePath",
                        new StringLiteralExpr(this.config.getResourcePath().get())));
        final VariableDeclarationExpr resourcePathDecl = new VariableDeclarationExpr(
                new NodeList<>(new com.github.javaparser.ast.Modifier(Modifier.Keyword.FINAL)),
                new NodeList<>(resourcePathVar));
        loadBody.addStatement(resourcePathDecl);

        final VariableDeclarator extractDirVar = new VariableDeclarator(
                parseType("java.nio.file.Path"), "extractionDir", new MethodCallExpr("getExtractionDirectory"));
        final VariableDeclarationExpr extractDirDecl = new VariableDeclarationExpr(
                new NodeList<>(new com.github.javaparser.ast.Modifier(Modifier.Keyword.FINAL)),
                new NodeList<>(extractDirVar));
        loadBody.addStatement(extractDirDecl);

        if (this.config.getEnableCaching().getOrElse(false)) {
            final VariableDeclarator cachedLibVar = new VariableDeclarator(
                    parseType("java.nio.file.Path"),
                    "cachedLib",
                    new MethodCallExpr(
                            "getCachedLibrary", new NameExpr("resourcePath"), new NameExpr("extractionDir")));
            final VariableDeclarationExpr cachedLibDecl = new VariableDeclarationExpr(
                    new NodeList<>(new com.github.javaparser.ast.Modifier(Modifier.Keyword.FINAL)),
                    new NodeList<>(cachedLibVar));
            loadBody.addStatement(cachedLibDecl);

            final BlockStmt ifCachedBody = new BlockStmt();
            ifCachedBody.addStatement(new MethodCallExpr(
                    new NameExpr("System"),
                    "load",
                    new NodeList<>(new MethodCallExpr(
                            new MethodCallExpr(new NameExpr("cachedLib"), "toAbsolutePath"), "toString"))));
            ifCachedBody.addStatement(new com.github.javaparser.ast.expr.AssignExpr(
                    new NameExpr("loaded"),
                    new BooleanLiteralExpr(true),
                    com.github.javaparser.ast.expr.AssignExpr.Operator.ASSIGN));
            ifCachedBody.addStatement(new ReturnStmt());

            final BinaryExpr condition = new BinaryExpr(
                    new BinaryExpr(new NameExpr("cachedLib"), new NameExpr("null"), BinaryExpr.Operator.NOT_EQUALS),
                    new MethodCallExpr(
                            new NameExpr("java.nio.file.Files"), "exists", new NodeList<>(new NameExpr("cachedLib"))),
                    BinaryExpr.Operator.AND);

            loadBody.addStatement(new IfStmt(condition, ifCachedBody, null));
        }

        final VariableDeclarator extractedLibVar = new VariableDeclarator(
                parseType("java.nio.file.Path"),
                "extractedLib",
                new MethodCallExpr("extractLibrary", new NameExpr("resourcePath"), new NameExpr("extractionDir")));
        final VariableDeclarationExpr extractedLibDecl = new VariableDeclarationExpr(
                new NodeList<>(new com.github.javaparser.ast.Modifier(Modifier.Keyword.FINAL)),
                new NodeList<>(extractedLibVar));
        loadBody.addStatement(extractedLibDecl);

        loadBody.addStatement(new MethodCallExpr(
                new NameExpr("System"),
                "load",
                new NodeList<>(new MethodCallExpr(
                        new MethodCallExpr(new NameExpr("extractedLib"), "toAbsolutePath"), "toString"))));
        loadBody.addStatement(new com.github.javaparser.ast.expr.AssignExpr(
                new NameExpr("loaded"),
                new BooleanLiteralExpr(true),
                com.github.javaparser.ast.expr.AssignExpr.Operator.ASSIGN));

        // expandResourcePath method
        final MethodDeclaration expandMethod =
                loaderClass.addMethod("expandResourcePath", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
        expandMethod.setType(String.class);
        expandMethod.addParameter(String.class, "template");
        expandMethod.getParameter(0).setFinal(true);
        final BlockStmt expandBody = expandMethod.getBody().get();

        expandBody.addStatement(parseStatement("final String osName = detectOsName();"));
        expandBody.addStatement(parseStatement("final String osArch = detectOsArch();"));
        expandBody.addStatement(parseStatement("String path = template.replace(\"{os.name}\", osName);"));
        expandBody.addStatement(parseStatement("path = path.replace(\"{os.arch}\", osArch);"));
        expandBody.addOrphanComment(new com.github.javaparser.ast.comments.LineComment(
                "Add platform-specific library extension and prefix"));
        expandBody.addStatement(parseStatement("final String libName = getLibraryFileName(path, osName);"));
        expandBody.addStatement(parseStatement("return libName;"));

        this.addOsDetectionMethods(loaderClass);
        this.addFileSystemMethods(loaderClass);

        if (this.config.getEnableCaching().getOrElse(false)) {
            this.addCachingMethods(loaderClass);
        }
    }

    private void addOsDetectionMethods(final ClassOrInterfaceDeclaration loaderClass) {
        final MethodDeclaration detectOsName =
                loaderClass.addMethod("detectOsName", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
        detectOsName.setType(String.class);
        final BlockStmt osNameBody = detectOsName.getBody().get();
        osNameBody.addStatement(parseStatement("final String osName = System.getProperty(\"os.name\").toLowerCase();"));

        final IfStmt ifWin = new IfStmt(
                parseExpression("osName.contains(\"win\")"),
                new BlockStmt().addStatement(new ReturnStmt(new StringLiteralExpr("windows"))),
                null);

        final IfStmt ifMac = new IfStmt(
                parseExpression("osName.contains(\"mac\") || osName.contains(\"darwin\")"),
                new BlockStmt().addStatement(new ReturnStmt(new StringLiteralExpr("macos"))),
                null);

        final IfStmt ifLinux = new IfStmt(
                parseExpression("osName.contains(\"nux\")"),
                new BlockStmt().addStatement(new ReturnStmt(new StringLiteralExpr("linux"))),
                null);

        ifMac.setElseStmt(ifLinux);
        ifWin.setElseStmt(ifMac);

        osNameBody.addStatement(ifWin);
        osNameBody.addStatement(
                parseStatement("throw new UnsupportedOperationException(\"Unsupported OS: \" + osName);"));

        final MethodDeclaration detectOsArch =
                loaderClass.addMethod("detectOsArch", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
        detectOsArch.setType(String.class);
        final BlockStmt osArchBody = detectOsArch.getBody().get();
        osArchBody.addStatement(parseStatement("final String osArch = System.getProperty(\"os.arch\").toLowerCase();"));

        final IfStmt ifAmd64 = new IfStmt(
                parseExpression("osArch.contains(\"amd64\") || osArch.contains(\"x86_64\")"),
                new BlockStmt().addStatement(new ReturnStmt(new StringLiteralExpr("amd64"))),
                null);

        final IfStmt ifArm64 = new IfStmt(
                parseExpression("osArch.contains(\"aarch64\") || osArch.contains(\"arm64\")"),
                new BlockStmt().addStatement(new ReturnStmt(new StringLiteralExpr("aarch64"))),
                null);

        final IfStmt ifX86 = new IfStmt(
                parseExpression("osArch.contains(\"x86\")"),
                new BlockStmt().addStatement(new ReturnStmt(new StringLiteralExpr("x86"))),
                null);

        ifArm64.setElseStmt(ifX86);
        ifAmd64.setElseStmt(ifArm64);

        osArchBody.addStatement(ifAmd64);
        osArchBody.addStatement(
                parseStatement("throw new UnsupportedOperationException(\"Unsupported architecture: \" + osArch);"));
    }

    private void addFileSystemMethods(final ClassOrInterfaceDeclaration loaderClass) {
        final MethodDeclaration getLibName =
                loaderClass.addMethod("getLibraryFileName", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
        getLibName.setType(String.class);
        getLibName.addParameter(String.class, "basePath");
        getLibName.addParameter(String.class, "osName");
        getLibName.getParameters().forEach(p -> p.setFinal(true));

        final BlockStmt libNameBody = getLibName.getBody().get();
        libNameBody.addStatement(
                parseStatement("final String fileName = basePath.substring(basePath.lastIndexOf('/') + 1);"));
        libNameBody.addStatement(
                parseStatement("final String dirPath = basePath.substring(0, basePath.lastIndexOf('/') + 1);"));

        final IfStmt ifWin = new IfStmt(
                parseExpression("osName.equals(\"windows\")"),
                new BlockStmt().addStatement(new ReturnStmt(parseExpression("dirPath + fileName + \".dll\""))),
                null);

        final IfStmt ifMac = new IfStmt(
                parseExpression("osName.equals(\"macos\")"),
                new BlockStmt()
                        .addStatement(new ReturnStmt(parseExpression("dirPath + \"lib\" + fileName + \".dylib\""))),
                null);

        final BlockStmt elseBlock =
                new BlockStmt().addStatement(new ReturnStmt(parseExpression("dirPath + \"lib\" + fileName + \".so\"")));

        ifMac.setElseStmt(elseBlock);
        ifWin.setElseStmt(ifMac);

        libNameBody.addStatement(ifWin);

        final MethodDeclaration getExtractionDir =
                loaderClass.addMethod("getExtractionDirectory", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
        getExtractionDir.setType(Path.class);
        getExtractionDir.addThrownException(IOException.class);
        final BlockStmt extractBody = getExtractionDir.getBody().get();

        if (this.config.getExtractionDir().isPresent()) {
            extractBody.addStatement(parseStatement("final java.nio.file.Path configuredDir = java.nio.file.Path.of(\""
                    + this.config
                            .getExtractionDir()
                            .get()
                            .getAsFile()
                            .getAbsolutePath()
                            .replace("\\", "\\\\") + "\");"));
            extractBody.addStatement(parseStatement("java.nio.file.Files.createDirectories(configuredDir);"));
            extractBody.addStatement(parseStatement("return configuredDir;"));
        } else {
            extractBody.addStatement(parseStatement("final String tmpDir = System.getProperty(\"java.io.tmpdir\");"));
            extractBody.addStatement(parseStatement(
                    "final java.nio.file.Path extractDir = java.nio.file.Path.of(tmpDir, \"jextract-natives\");"));
            extractBody.addStatement(parseStatement("java.nio.file.Files.createDirectories(extractDir);"));
            extractBody.addStatement(parseStatement("return extractDir;"));
        }

        final MethodDeclaration extractLib =
                loaderClass.addMethod("extractLibrary", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
        extractLib.setType(Path.class);
        extractLib.addThrownException(IOException.class);
        extractLib.addParameter(String.class, "resourcePath");
        extractLib.addParameter(Path.class, "extractionDir");
        extractLib.getParameters().forEach(p -> p.setFinal(true));

        final BlockStmt extractLibBody = extractLib.getBody().get();
        extractLibBody.addStatement(
                parseStatement("final String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);"));
        extractLibBody.addStatement(
                parseStatement("final java.nio.file.Path targetFile = extractionDir.resolve(fileName);"));

        final TryStmt tryResource = new TryStmt();
        final VariableDeclarator inVar = new VariableDeclarator(
                parseType("java.io.InputStream"),
                "in",
                new MethodCallExpr(
                        new FieldAccessExpr(new NameExpr(this.headerClassName + "_NativeLibraryLoader"), "class"),
                        "getResourceAsStream",
                        new NodeList<>(new BinaryExpr(
                                new StringLiteralExpr("/"), new NameExpr("resourcePath"), BinaryExpr.Operator.PLUS))));
        tryResource.getResources().add(new VariableDeclarationExpr(inVar));

        final BlockStmt tryBody = new BlockStmt();
        tryBody.addStatement(parseStatement(
                "if (in == null) throw new java.io.IOException(\"Resource not found: \" + resourcePath);"));
        tryBody.addStatement(parseStatement(
                "java.nio.file.Files.copy(in, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);"));
        tryResource.setTryBlock(tryBody);

        extractLibBody.addStatement(tryResource);
        extractLibBody.addStatement(parseStatement("return targetFile;"));
    }

    private void addCachingMethods(final ClassOrInterfaceDeclaration loaderClass) {
        final MethodDeclaration getCached =
                loaderClass.addMethod("getCachedLibrary", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
        getCached.setType(Path.class);
        getCached.addThrownException(IOException.class);
        getCached.addParameter(String.class, "resourcePath");
        getCached.addParameter(Path.class, "extractionDir");
        getCached.getParameters().forEach(p -> p.setFinal(true));

        final BlockStmt cachedBody = getCached.getBody().get();
        final TryStmt tryResource = new TryStmt();
        // Create try-with-resources variable declaration
        final VariableDeclarator inVar = new VariableDeclarator(
                parseType("java.io.InputStream"),
                "in",
                new MethodCallExpr(
                        new FieldAccessExpr(new NameExpr(this.headerClassName + "_NativeLibraryLoader"), "class"),
                        "getResourceAsStream",
                        new NodeList<>(new BinaryExpr(
                                new StringLiteralExpr("/"), new NameExpr("resourcePath"), BinaryExpr.Operator.PLUS))));
        tryResource.getResources().add(new VariableDeclarationExpr(inVar));

        final BlockStmt tryBody = new BlockStmt();
        tryBody.addStatement(parseStatement("if (in == null) return null;"));
        tryBody.addStatement(parseStatement("final String hash = computeHash(in);"));
        tryBody.addStatement(
                parseStatement("final String fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);"));
        tryBody.addStatement(parseStatement(
                "final java.nio.file.Path cachedFile = extractionDir.resolve(fileName + \".\" + hash);"));
        tryBody.addStatement(parseStatement("return cachedFile;"));
        tryResource.setTryBlock(tryBody);
        cachedBody.addStatement(tryResource);

        final MethodDeclaration computeHash =
                loaderClass.addMethod("computeHash", Modifier.Keyword.PRIVATE, Modifier.Keyword.STATIC);
        computeHash.setType(String.class);
        computeHash.addThrownException(IOException.class);
        computeHash.addParameter(InputStream.class, "in");
        computeHash.getParameters().forEach(p -> p.setFinal(true));

        final BlockStmt hashBody = computeHash.getBody().get();
        final TryStmt tryHash = new TryStmt();
        final BlockStmt tryHashBody = new BlockStmt();
        tryHashBody.addStatement(parseStatement(
                "final java.security.MessageDigest digest = java.security.MessageDigest.getInstance(\"SHA-256\");"));
        tryHashBody.addStatement(parseStatement("final byte[] buffer = new byte[8192];"));
        tryHashBody.addStatement(parseStatement("int read;"));
        tryHashBody.addStatement(
                parseStatement("while ((read = in.read(buffer)) != -1) { digest.update(buffer, 0, read); }"));
        tryHashBody.addStatement(parseStatement("final byte[] hashBytes = digest.digest();"));
        tryHashBody.addStatement(parseStatement("final StringBuilder hex = new StringBuilder();"));
        tryHashBody.addStatement(
                parseStatement("for (byte b : hashBytes) { hex.append(String.format(\"%02x\", b)); }"));
        tryHashBody.addStatement(parseStatement("return hex.toString();"));
        tryHash.setTryBlock(tryHashBody);

        final CatchClause catchHash = new CatchClause();
        catchHash.setParameter(new Parameter(parseType("java.security.NoSuchAlgorithmException"), "e"));
        final BlockStmt catchHashBody = new BlockStmt();
        catchHashBody.addStatement(parseStatement("throw new java.io.IOException(\"SHA-256 not available\", e);"));
        catchHash.setBody(catchHashBody);
        tryHash.setCatchClauses(new NodeList<>(catchHash));

        hashBody.addStatement(tryHash);
    }

    private Path createPackageDirectory() throws IOException {
        final String[] packageParts = this.targetPackage.split("\\.");
        Path current = this.outputDirectory;
        for (final String part : packageParts) {
            current = current.resolve(part);
        }
        Files.createDirectories(current);
        return current;
    }
}
