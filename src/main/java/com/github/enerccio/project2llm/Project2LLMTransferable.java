package com.github.enerccio.project2llm;

import com.github.enerccio.project2llm.processor.FolderProcessorManager;
import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreeNode;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.List;

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
            File tempFile = createTextRepresentation();
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
    private File createTextRepresentation() {
        try {
            List<File> sourceFiles = null;
            if (attachedObject instanceof TransferableWrapper) {
                sourceFiles = ((TransferableWrapper) attachedObject).asFileList();
            } else if (attachedObject instanceof Transferable && ((Transferable) attachedObject).isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                sourceFiles = (List<File>) ((Transferable) attachedObject).getTransferData(DataFlavor.javaFileListFlavor);
            }

            if (sourceFiles == null || sourceFiles.isEmpty()) return null;
            return FolderProcessorManager.getInstance(project).createFolderContext(project, sourceFiles);
        } catch (Exception e) {
            e.printStackTrace();
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