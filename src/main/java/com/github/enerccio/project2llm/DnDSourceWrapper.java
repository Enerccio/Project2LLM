package com.github.enerccio.project2llm;

import com.intellij.ide.dnd.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

import java.awt.Point;
import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DragSourceDropEvent;
import java.io.File;
import java.util.List;

public class DnDSourceWrapper implements DnDSource {
    private final DnDSource delegate;
    private final Project project;

    public DnDSourceWrapper(Project project, DnDSource delegate) {
        this.delegate = delegate;
        this.project = project;
    }

    @Override
    public boolean canStartDragging(DnDAction action, Point dragOrigin) {
        return delegate.canStartDragging(action, dragOrigin);
    }

    @Override
    public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
        DnDDragStartBean originalBean = delegate.startDragging(action, dragOrigin);
        if (originalBean == null) return null;

        Object attachedObject = originalBean.getAttachedObject();

        if (isSingleNormalFile(attachedObject)) {
            return originalBean;
        }

        return new DnDDragStartBean(new Project2LLMTransferable(project, attachedObject));
    }

    private boolean isSingleNormalFile(Object attachedObject) {
        try {
            List<File> files = null;
            if (attachedObject instanceof TransferableWrapper) {
                files = ((TransferableWrapper) attachedObject).asFileList();
            } else if (attachedObject instanceof Transferable) {
                Transferable t = (Transferable) attachedObject;
                if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    @SuppressWarnings("unchecked")
                    List<File> casted = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    files = casted;
                }
            }

            // If exactly one item is dragged, and it's a file (not a folder), return true
            if (files != null && files.size() == 1) {
                File singleFile = files.get(0);
                return singleFile.isFile();
            }
        } catch (Exception ignored) {}

        return false;
    }

    @Override
    public @Nullable Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin, DnDDragStartBean bean) {
        return delegate.createDraggedImage(action, dragOrigin, bean);
    }

    @Override
    public void dragDropEnd(@Nullable DnDEvent dragEvent, @Nullable DragSourceDropEvent dropEvent) {
        delegate.dragDropEnd(dragEvent, dropEvent);
    }

    @Override
    public void dropActionChanged(int gestureModifiers) {
        delegate.dropActionChanged(gestureModifiers);
    }
}