package com.github.enerccio.project2llm;

import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Project2LLMTransferable implements Transferable, TransferableWrapper {
    private final Project project;
    private final Object attachedObject;

    public Project2LLMTransferable(Project project, Object attachedObject) {
        this.project = project;
        this.attachedObject = attachedObject;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[]{DataFlavor.javaFileListFlavor};
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return flavor.equals(DataFlavor.javaFileListFlavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (flavor.equals(DataFlavor.javaFileListFlavor)) {

            // 1. If it's an internal drop target, let IntelliJ do its normal work!
            if (isInternalDrop()) {
                return extractOriginalFiles();
            }

            // 2. Otherwise, it's an external drop—compile the repo2txt payload
            File tempFile = createRepo2TxtTempFile();
            if (tempFile != null) return List.of(tempFile);
        }
        throw new UnsupportedFlavorException(flavor);
    }

    private boolean isInternalDrop() {
        // Check 1: External apps request data on native system peer threads, not the Swing EDT
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            return false;
        }

        // Check 2: Verify if the mouse pointer is physically inside any open IntelliJ window bounds
        try {
            java.awt.Point mouseLoc = java.awt.MouseInfo.getPointerInfo().getLocation();
            for (java.awt.Window window : java.awt.Window.getWindows()) {
                if (window.isShowing() && window.getBounds().contains(mouseLoc)) {
                    return true; // Mouse is over an IDE frame during data retrieval
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    @SuppressWarnings("unchecked")
    private File createRepo2TxtTempFile() {
        return ApplicationManager.getApplication().runReadAction((Computable<File>) () -> {
            try {
                List<File> sourceFiles = null;
                if (attachedObject instanceof TransferableWrapper) {
                    sourceFiles = ((TransferableWrapper) attachedObject).asFileList();
                } else if (attachedObject instanceof Transferable && ((Transferable) attachedObject).isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    sourceFiles = (List<File>) ((Transferable) attachedObject).getTransferData(DataFlavor.javaFileListFlavor);
                }

                if (sourceFiles == null || sourceFiles.isEmpty()) return null;

                File tempFile = File.createTempFile("repo2txt-", ".txt");
                tempFile.deleteOnExit();

                StringBuilder metaBuilder = new StringBuilder();
                StringBuilder treeBuilder = new StringBuilder();
                StringBuilder contentBuilder = new StringBuilder();

                VirtualFile projectRoot = ProjectUtil.guessProjectDir(project);
                boolean isProjectRootIncluded = false;
                int[] stats = new int[]{0, 0}; // [Total Files, Total Bytes]

                List<VirtualFile> targetRoots = new ArrayList<>();
                for (File f : sourceFiles) {
                    VirtualFile vf = VfsUtil.findFileByIoFile(f, true);
                    if (vf != null) {
                        targetRoots.add(vf);
                        if (projectRoot != null && vf.equals(projectRoot)) {
                            isProjectRootIncluded = true;
                        }
                    }
                }

                // UNIFIED SINGLE PASS PROCESSING
                treeBuilder.append("========================================\n");
                treeBuilder.append("PROJECT ARCHITECTURE DIRECTORY TREE\n");
                treeBuilder.append("========================================\n");

                for (VirtualFile rootFile : targetRoots) {
                    // Seed initial patterns by walking UP from the drag target to the project root
                    List<Pattern> initialPatterns = collectParentIgnorePatterns(rootFile, projectRoot);

                    // Execute the single unified recursive scan pass
                    traverseAndProcess(rootFile, "", "", true, true, treeBuilder, contentBuilder, stats, initialPatterns, projectRoot);
                }
                treeBuilder.append("========================================\n\n\n");

                // Generate Metadata Header
                metaBuilder.append("========================================================================\n");
                metaBuilder.append("🤖 LLM INGESTION METADATA HEADER\n");
                metaBuilder.append("========================================================================\n");
                metaBuilder.append("INSTRUCTION: This file is a consolidated repository layout payload generated\n");
                metaBuilder.append("directly from an IntelliJ IDEA workspace. Use the provided directory tree\n");
                metaBuilder.append("to map file inheritance and architectures before executing requested operations.\n\n");

                metaBuilder.append("--- WORKSPACE SPECIFICATIONS ---\n");
                metaBuilder.append("Project Name:       ").append(project.getName()).append("\n");
                metaBuilder.append("Target Context:     ").append(isProjectRootIncluded ? "PROJECT ROOT FOLDER" : "SUB-DIRECTORY SELECTION").append("\n");

                Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
                if (projectSdk != null) {
                    metaBuilder.append("Project SDK/Runtime:").append(projectSdk.getName()).append("\n");
                }

                if (isProjectRootIncluded) {
                    metaBuilder.append("Detected Ecosystem: ").append(detectProjectEcosystem(projectRoot)).append("\n");
                }

                metaBuilder.append("Payload Summary:    ").append(stats[0]).append(" files packed | ")
                        .append(String.format("%.2f", stats[1] / 1024.0)).append(" KB total size\n");
                metaBuilder.append("========================================================================\n\n\n");

                Files.writeString(tempFile.toPath(), metaBuilder.toString() + treeBuilder + contentBuilder, StandardCharsets.UTF_8);
                return tempFile;

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        });
    }

    /**
     * UNIFIED TRAVERSAL PASS: Assembles Tree Layout and reads Data in one single VFS loop.
     */
    private void traverseAndProcess(VirtualFile file, String currentPath, String indent, boolean isLast, boolean isRoot,
                                    StringBuilder treeBuilder, StringBuilder contentBuilder, int[] stats,
                                    List<Pattern> activePatterns, VirtualFile projectRoot) {

        FileTypeManager fileTypeManager = FileTypeManager.getInstance();
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);

        // 1. Core IDE Exclusions and Hardcoded Filters
        if (fileTypeManager.isFileIgnored(file) || fileIndex.isExcluded(file)) return;
        String name = file.getName();
        if (name.equals(".git") || name.equals(".idea") || name.endsWith(".iml") || name.equals(".aiignore")) return;

        // 2. Cascading .aiignore Evaluation Check
        if (projectRoot != null) {
            String relativeToProjectRoot = VfsUtil.getRelativePath(file, projectRoot);
            if (relativeToProjectRoot != null) {
                for (Pattern pattern : activePatterns) {
                    if (pattern.matcher(relativeToProjectRoot).find()) return; // Matches ignore rule
                }
            }
        }

        String nextPath = currentPath.isEmpty() ? name : currentPath + "/" + name;

        // 3. Document the Visual Branch immediately
        if (isRoot) {
            treeBuilder.append(name).append(file.isDirectory() ? "/\n" : "\n");
        } else {
            treeBuilder.append(indent).append(isLast ? "└── " : "├── ").append(name).append(file.isDirectory() ? "/\n" : "\n");
        }

        // 4. Diverge Processing based on File Type
        if (file.isDirectory()) {
            // Local .aiignore Discovery: Load and append rules down to local clone array
            List<Pattern> nestedPatterns = new ArrayList<>(activePatterns);
            VirtualFile localIgnoreFile = file.findChild(".aiignore");
            if (localIgnoreFile != null && !localIgnoreFile.isDirectory()) {
                nestedPatterns.addAll(loadAiIgnorePatterns(localIgnoreFile));
            }

            // Collect valid nodes ahead of time to maintain accurate branching indicators
            List<VirtualFile> validChildren = new ArrayList<>();
            for (VirtualFile child : file.getChildren()) {
                if (!fileTypeManager.isFileIgnored(child) && !fileIndex.isExcluded(child)) {
                    String cName = child.getName();
                    if (!cName.equals(".git") && !cName.equals(".idea") && !cName.endsWith(".iml") && !cName.equals(".aiignore")) {
                        // Pre-evaluate ignore flags for the children so our branch math matches perfectly
                        if (projectRoot != null) {
                            String childRelPath = VfsUtil.getRelativePath(child, projectRoot);
                            if (childRelPath != null) {
                                boolean skipChild = false;
                                for (Pattern p : nestedPatterns) {
                                    if (p.matcher(childRelPath).find()) { skipChild = true; break; }
                                }
                                if (skipChild) continue;
                            }
                        }
                        validChildren.add(child);
                    }
                }
            }

            String nextIndent = isRoot ? "" : indent + (isLast ? "    " : "│   ");
            for (int i = 0; i < validChildren.size(); i++) {
                traverseAndProcess(validChildren.get(i), nextPath, nextIndent, i == validChildren.size() - 1, false,
                        treeBuilder, contentBuilder, stats, nestedPatterns, projectRoot);
            }
        } else if (!file.getFileType().isBinary()) {
            // Document non-binary source file data
            stats[0] += 1; // Increment accurate payload files count

            // Maximum threshold performance safeguard check (2 MB limit)
            if (file.getLength() > 2 * 1024 * 1024) {
                double fileSizeMb = file.getLength() / (1024.0 * 1024.0);
                StringBuilder stub = new StringBuilder();
                stub.append("========================================\n");
                stub.append("File: ").append(nextPath).append(" [OMITTED FROM CONTENT]\n");
                stub.append("========================================\n");
                stub.append("[NOTICE TO LLM: This text file was intentionally omitted from the content payload\n");
                stub.append("because its physical size (").append(String.format("%.2f", fileSizeMb)).append(" MB) exceeds the 2 MB maximum performance\n");
                stub.append("threshold. The file exists in the directory tree structure, but its contents\n");
                stub.append("have been replaced with this stub to preserve your attention context limits.]\n\n\n");

                String stubStr = stub.toString();
                contentBuilder.append(stubStr);
                stats[1] += stubStr.getBytes(StandardCharsets.UTF_8).length;
                return;
            }

            try {
                byte[] bytes = file.contentsToByteArray();
                String contentStr = new String(bytes, file.getCharset());

                // Count lines efficiently
                long lineCount = contentStr.lines().count();
                stats[1] += bytes.length;

                contentBuilder.append("========================================\n");
                contentBuilder.append("File: ").append(nextPath).append("\n");
                contentBuilder.append("Type: ").append(file.getFileType().getName()).append(" | Lines: ").append(lineCount).append("\n");
                contentBuilder.append("========================================\n");
                contentBuilder.append(contentStr).append("\n\n");
            } catch (IOException ignored) {}
        }
    }

    /**
     * Walks UP from target directory to Project Root to assemble historical gov patterns.
     */
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

    @SuppressWarnings("unchecked")
    private List<File> extractOriginalFiles() throws IOException, UnsupportedFlavorException {
        if (attachedObject instanceof TransferableWrapper) {
            return ((TransferableWrapper) attachedObject).asFileList();
        } else if (attachedObject instanceof Transferable && ((Transferable) attachedObject).isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return (List<File>) ((Transferable) attachedObject).getTransferData(DataFlavor.javaFileListFlavor);
        }
        return List.of();
    }

    @Override
    public TreeNode @Nullable [] getTreeNodes() {
        return attachedObject instanceof TransferableWrapper tw ? tw.getTreeNodes() : new TreeNode[0];
    }

    @Override
    public PsiElement @Nullable [] getPsiElements() {
        return attachedObject instanceof TransferableWrapper tw ? tw.getPsiElements() : new PsiElement[0];
    }

    @Override
    public @Nullable List<File> asFileList() {
        return attachedObject instanceof TransferableWrapper tw ? tw.asFileList() : List.of();
    }
}