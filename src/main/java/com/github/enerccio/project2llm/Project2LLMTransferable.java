package com.github.enerccio.project2llm;

import com.github.enerccio.project2llm.processor.FolderProcessorManager;
import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class Project2LLMTransferable implements Transferable, TransferableWrapper {
    private static final Logger LOG = Logger.getInstance(Project2LLMTransferable.class);

    private final AtomicInteger macCallCounter = new AtomicInteger(0);
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
    public @NotNull Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (flavor.equals(DataFlavor.javaFileListFlavor)) {
            LOG.info("getTransferData called.");

            if (isInternalDrop()) {
                LOG.info("Internal drop");
                return extractOriginalFiles();
            }

            if (DumbService.getInstance(project).isDumb()) {
                LOG.info("Index building in progress (Dumb Mode). Skipping LLM context generation to prevent UI stall.");

                // Returning null here forces getTransferData to skip the LLM text file conversion
                // and safely fall back to returning the original files/folders instead!
                return extractOriginalFiles();
            }

            File tempFile = createTextRepresentation();
            if (tempFile != null) {
                LOG.info("Folder context text representation created successfully. Returning to internal consumer.");
                return List.of(tempFile);
            }
        }
        throw new UnsupportedFlavorException(flavor);
    }

    private boolean isInternalDrop() {
        // 1. macOS Handling: Use the invocation counter gate
        if (com.intellij.openapi.util.SystemInfo.isMac) {
            int currentCall = macCallCounter.incrementAndGet();
            return currentCall > 1;
        }

        // 2. Standard Event Dispatch Thread Handling (Windows & Linux X11)
        // If we are directly on the UI thread, we can safely use local stack checking.
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            StackTraceElement[] localStack = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : localStack) {
                String methodName = element.getMethodName().toLowerCase();
                if (element.getClassName().contains("DropTarget") || methodName.contains("drop")) {
                    return true;
                }
            }
            return false;
        }

        // 3. Linux Wayland Handling
        // We are on a background thread. Only perform the heavy cross-thread inspection
        // if we are definitively running on Linux under a Wayland session.
        boolean isLinux = com.intellij.openapi.util.SystemInfo.isLinux;
        boolean isWayland = "wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"));

        if (isLinux && isWayland) {
            try {
                // Target only the AWT EventQueue thread to minimize loop overhead
                for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                    Thread thread = entry.getKey();

                    if (thread.getName().contains("AWT-EventQueue")) {
                        for (StackTraceElement element : entry.getValue()) {
                            String className = element.getClassName();
                            String methodName = element.getMethodName().toLowerCase();

                            if (className.contains("DropTarget") ||
                                    methodName.contains("drop") ||
                                    className.contains("fileEditor") ||
                                    (className.contains("com.intellij.ide.dnd") && !methodName.contains("startdrag"))) {
                                LOG.info("Wayland Guard: Internal drop caught via main thread cross-examination.");
                                return true;
                            }
                        }
                        break; // Found the EDT, no need to keep scanning other threads
                    }
                }
            } catch (Exception ignored) {}
        }

        // Default fallback (External drop destination)
        return false;
    }

    @SuppressWarnings("unchecked")
    private File createTextRepresentation() {
        try {
            List<File> sourceFiles = null;
            if (attachedObject instanceof TransferableWrapper) {
                sourceFiles = ((TransferableWrapper) attachedObject).asFileList();
            } else if (attachedObject instanceof Transferable && ((Transferable) attachedObject).isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                sourceFiles = (List<File>) ((Transferable) attachedObject).getTransferData(DataFlavor.javaFileListFlavor);
            }

            if (sourceFiles == null || sourceFiles.isEmpty()) return null;

            LOG.info("Creating folder context text representation for " + sourceFiles.size() + " files");
            LOG.debug("Files: " + sourceFiles);
            return FolderProcessorManager.getInstance(project).createFolderContext(project, sourceFiles);
        } catch (Exception e) {
            LOG.error("Failed to create folder context text representation", e);
            return null;
        }
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