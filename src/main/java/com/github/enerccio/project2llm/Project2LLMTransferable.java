package com.github.enerccio.project2llm;

import com.github.enerccio.project2llm.processor.FolderProcessorManager;
import com.intellij.ide.dnd.TransferableWrapper;
import com.intellij.openapi.diagnostic.Logger;
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
import java.util.concurrent.atomic.AtomicInteger;

public class Project2LLMTransferable implements Transferable, TransferableWrapper {
    private static final Logger LOG = Logger.getInstance(Project2LLMTransferable.class);

    private final AtomicInteger callCounter = new AtomicInteger(0);
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
            int currentCall = callCounter.incrementAndGet();
            LOG.info("getTransferData called. Processing invocation #" + currentCall);

            if (currentCall > 1) {
                LOG.info("Subsequent local invocation detected. Returning original files to internal consumer.");
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