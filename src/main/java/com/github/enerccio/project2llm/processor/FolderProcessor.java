package com.github.enerccio.project2llm.processor;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

public class FolderProcessor {

    private final Project project;
    private final List<File> sourceFiles;
    private final ProcessorContext context;
    private final VelocityEngine velocityEngine;

    private File tempFile;
    private StringBuilder metaBuilder = new StringBuilder();
    private StringBuilder treeBuilder = new StringBuilder();
    private StringBuilder contentBuilder = new StringBuilder();
    private int files;
    private long bytes;
    private boolean isProjectRootIncluded;
    private VirtualFile projectRoot;
    private List<VirtualFile> moduleRoots;
    private boolean hasModulesIncluded;
    private Set<Module> includedModules = new HashSet<>();
    private Map<VirtualFile, Module> vf2module = new HashMap<>();
    private FileTypeManager fileTypeManager;
    private ProjectFileIndex fileIndex;

    private List<FileData> contents = new ArrayList<>();

    public FolderProcessor(Project project, List<File> sourceFiles, ProcessorContext context, VelocityEngine velocityEngine) {
        this.project = project;
        this.sourceFiles = sourceFiles;
        this.context = context;
        this.velocityEngine = velocityEngine;
    }

    public File process() {
        try {
            return exportFolderContents();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private File exportFolderContents() throws Exception {
        projectRoot = ProjectUtil.guessProjectDir(project);
        fileTypeManager = FileTypeManager.getInstance();
        fileIndex = ProjectFileIndex.getInstance(project);

        Module[] modules = ModuleManager.getInstance(project).getModules();
        if (modules != null) {
            moduleRoots = new ArrayList<>();
            for (Module module : modules) {
                VirtualFile mf = ProjectUtil.guessModuleDir(module);
                vf2module.put(mf, module);
                moduleRoots.add(mf);
            }
        }

        List<VirtualFile> targetRoots = new ArrayList<>();
        for (File f : sourceFiles) {
            VirtualFile vf = VfsUtil.findFileByIoFile(f, true);
            if (vf != null) {
                targetRoots.add(vf);
                if (vf.equals(projectRoot)) {
                    isProjectRootIncluded = true;
                }
                for (VirtualFile mf : moduleRoots) {
                    if (mf.equals(vf)) {
                        hasModulesIncluded = true;
                        includedModules.add(vf2module.get(mf));
                    }
                }
            }
        }

        for (VirtualFile root : targetRoots) {
            loadContent(root);
        }

        tempFile = File.createTempFile("project2llm-", context.getSuffix());
        tempFile.deleteOnExit();

        saveContent();

        return tempFile;
    }

    private void loadContent(VirtualFile file) throws Exception {
        List<Pattern> initialPatterns = collectParentIgnorePatterns(file, projectRoot);
        traverseTree(file, "", "", true, true, initialPatterns);
    }

    private void traverseTree(VirtualFile file, String currentPath, String ident, boolean isLast, boolean isRoot, List<Pattern> activePatterns) {
        if (isSkipped(file, activePatterns)) {
            return;
        }

        String name = file.getName();
        String nextPath = currentPath.isEmpty() ? name : currentPath + context.getSeparator() + name;
        assert projectRoot != null;
        FileData fileData = new FileData(file, projectRoot, ident + (isLast ? context.getTreeLeaf() : context.getTreeBranch()),
                file.isDirectory() ? context.getSeparator() : "");
        files++;

        contents.add(fileData);

        if (file.isDirectory()) {
            // update aiignore if aiignore is there
            List<Pattern> nestedPatterns = new ArrayList<>(activePatterns);
            VirtualFile localIgnoreFile = file.findChild(".aiignore");
            if (localIgnoreFile != null && !localIgnoreFile.isDirectory()) {
                nestedPatterns.addAll(loadAiIgnorePatterns(localIgnoreFile));
            }

            // Collect valid nodes ahead of time to maintain accurate branching indicators
            List<VirtualFile> validChildren = new ArrayList<>();
            for (VirtualFile child : file.getChildren()) {
                if (isSkipped(child, nestedPatterns)) {
                    continue;
                }
                validChildren.add(child);
            }

            String nextIdent = isRoot ? "" : ident + (isLast ? context.getTreeSpacing() : context.getTreeNext());
            for (int i = 0; i < validChildren.size(); i++) {
                traverseTree(validChildren.get(i), nextPath, nextIdent, i == validChildren.size() - 1, false, nestedPatterns);
            }
        }
    }

    private boolean isSkipped(VirtualFile file, List<Pattern> activePatterns) {
        if (fileTypeManager.isFileIgnored(file) || fileIndex.isExcluded(file)) return true;
        String name = file.getName();
        if (name.equals(".git") || name.equals(".idea") || name.endsWith(".iml") || name.equals(".aiignore"))
            return true;

        // aiignore check
        if (projectRoot != null) {
            String relativeToProjectRoot = VfsUtil.getRelativePath(file, projectRoot);
            if (relativeToProjectRoot != null) {
                for (Pattern pattern : activePatterns) {
                    if (pattern.matcher(relativeToProjectRoot).find()) return true; // Matches ignore rule
                }
            }
        }

        return false;
    }

    private void saveContent() throws Exception {
        // build contents first
        for (FileData fileData : contents) {
            if (fileData.isFolder()) continue;

            StringWriter writer = new StringWriter();
            if (fileData.isBinary()) {
                VelocityContext context = new VelocityContext();
                context.put("file", fileData.getFileName());
                context.put("type", fileData.getMimeType());
                context.put("description", fileData.getDescription());
                context.put("fileSizeMb", fileData.getFileSize() / (1024 * 1024));
                velocityEngine.evaluate(context, writer, "Project2LLM_BinaryFile", this.context.getBinaryFile());
            } else if (fileData.getFileSize() > this.context.getMaxFileSize()) {
                VelocityContext context = new VelocityContext();
                context.put("file", fileData.getFileName());
                context.put("type", fileData.getMimeType());
                context.put("fileSizeMb", fileData.getFileSize() / (1024 * 1024));
                context.put("maxFileSize", this.context.getMaxFileSize() / (1024 * 1024));
                velocityEngine.evaluate(context, writer, "Project2LLM_SourceTooLarge", this.context.getSourceTooLargeTemplate());
            } else {
                VelocityContext context = new VelocityContext();
                context.put("file", fileData.getFileName());
                context.put("type", fileData.getMimeType());
                context.put("content", fileData.getContents());
                context.put("lineCount", fileData.getLines());
                context.put("extension", fileData.getExt());
                velocityEngine.evaluate(context, writer, "Project2LLM_SourceFile", this.context.getSourceFileTemplate());
                bytes += fileData.getFileSize();
            }
            contentBuilder.append(writer).append("\n\n");
        }

        // build tree
        VelocityContext treeContext = new VelocityContext();
        StringBuilder treeStructureBuilder = new StringBuilder();
        for (FileData fileData : contents) {
            treeStructureBuilder.append(fileData.printToTree());
        }
        treeContext.put("tree", treeStructureBuilder.toString());

        StringWriter treeWriter = new StringWriter();
        velocityEngine.evaluate(treeContext, treeWriter, "Project2LLM_SourceTree", context.getTreeTemplate());
        treeBuilder.append(treeWriter);

        // build meta
        String product = ApplicationNamesInfo.getInstance().getFullProductName();
        Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
        String targetContext = isProjectRootIncluded ? "PROJECT ROOT FOLDER" : "SUB-DIRECTORY SELECTION";
        String ecosystem = detectProjectEcosystem(projectRoot);

        VelocityContext metaContext = new VelocityContext();
        metaContext.put("product", product);
        metaContext.put("projectName", project.getName());
        metaContext.put("targetContext", targetContext);
        if (projectSdk != null) {
            metaContext.put("sdk", projectSdk.getName());
        }
        metaContext.put("detectedEcosystem", ecosystem);
        metaContext.put("fileCount", files);
        metaContext.put("totalSizeKb", bytes / (1024));
        metaContext.put("hasModuleRoots", hasModulesIncluded);
        if (hasModulesIncluded) {
            List<ModuleInfo> modules = new ArrayList<>();
            for (Module module : includedModules) {
                modules.add(new ModuleInfo(module, ProjectUtil.guessModuleDir(module) , projectRoot, context.getSeparator()));
            }
            metaContext.put("modules", modules);
        }

        StringWriter metaWriter = new StringWriter();
        velocityEngine.evaluate(metaContext, metaWriter, "Project2LLM_Meta", context.getMetaInfoTemplate());
        metaBuilder.append(metaWriter);

        Files.writeString(tempFile.toPath(), context.getTopHeaderTemplate() + "\n" +
                metaBuilder.toString() + "\n" + treeBuilder.toString() + "\n" +  contentBuilder.toString(), StandardCharsets.UTF_8);
    }

    private List<Pattern> collectParentIgnorePatterns(VirtualFile targetRoot, VirtualFile projectRoot) {
        List<Pattern> collectedPatterns = new ArrayList<>();
        if (projectRoot == null || targetRoot == null) return collectedPatterns;

        VirtualFile current = targetRoot;
        List<VirtualFile> ignoreFilesToProcess = new ArrayList<>();

        while (current != null) {
            VirtualFile ignoreFile = current.findChild(".aiignore");
            if (ignoreFile != null && !ignoreFile.isDirectory()) {
                ignoreFilesToProcess.add(ignoreFile);
            }
            if (current.equals(projectRoot)) break;
            current = current.getParent();
        }

        // Process from top-down (Project root configurations get appended first)
        for (int i = ignoreFilesToProcess.size() - 1; i >= 0; i--) {
            collectedPatterns.addAll(loadAiIgnorePatterns(ignoreFilesToProcess.get(i)));
        }
        return collectedPatterns;
    }

    private List<Pattern> loadAiIgnorePatterns(VirtualFile file) {
        List<Pattern> patterns = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), file.getCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                Pattern p = compileGlobToRegex(trimmed);
                patterns.add(p);
            }
        } catch (IOException ignored) {}
        return patterns;
    }

    private Pattern compileGlobToRegex(String patternLine) {
        String line = patternLine.trim();
        StringBuilder sb = new StringBuilder();
        if (!line.startsWith("/")) {
            sb.append("(^|.*/)");
        } else {
            line = line.substring(1);
        }
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '*') {
                if (i + 1 < line.length() && line.charAt(i + 1) == '*') {
                    sb.append(".*");
                    i++;
                } else {
                    sb.append("[^/]*");
                }
            } else if (c == '?') {
                sb.append("[^/]");
            } else if (c == '.') {
                sb.append("\\.");
            } else if ("\\[](){}^$-+|".indexOf(c) != -1) {
                sb.append("\\").append(c);
            } else {
                sb.append(c);
            }
        }
        sb.append("($|/.*)");
        return Pattern.compile(sb.toString());
    }

    private String detectProjectEcosystem(VirtualFile root) {
        List<String> systems = new ArrayList<>();
        if (root.findChild("pom.xml") != null) systems.add("Maven (Java)");
        if (root.findChild("build.gradle") != null || root.findChild("build.gradle.kts") != null) systems.add("Gradle (Java/Kotlin)");
        if (root.findChild("package.json") != null) systems.add("Node.js/npm (JavaScript/TypeScript)");
        if (root.findChild("cargo.toml") != null) systems.add("Cargo (Rust)");
        if (root.findChild("go.mod") != null) systems.add("Go Modules (Golang)");
        if (root.findChild("requirements.txt") != null || root.findChild("pyproject.toml") != null) systems.add("Python");

        return systems.isEmpty() ? "Generic/Polyglot Workspace" : String.join(", ", systems);
    }
}
