package com.github.enerccio.project2llm.processor;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class FileData {

    private final VirtualFile vf;
    private final String treePrefix;
    private final String treeSuffix;
    private final String fileName;
    private final String mimeType;
    private final String description;
    private final String localPath;
    private final boolean isFolder;
    private final long fileSize;
    private long lines;
    private String contents;
    private final boolean isBinary;
    private final String ext;

    public FileData(@NotNull VirtualFile vf, @NotNull VirtualFile projectRoot, String treePrefix, String treeSuffix) {
        this.vf = vf;
        this.treePrefix = treePrefix;
        this.treeSuffix = treeSuffix;
        this.fileName = vf.getName();
        this.localPath = VfsUtil.getRelativePath(vf, projectRoot);
        this.isFolder = vf.isDirectory();
        if (!this.isFolder) {
            this.mimeType = vf.getFileType().getName();
            this.description = vf.getFileType().getDescription();
            this.isBinary = vf.getFileType().isBinary();
            this.fileSize = vf.getLength();
            this.ext = vf.getExtension() == null ? "" : (vf.getExtension().startsWith(".") ? vf.getExtension().substring(1) : vf.getExtension());
        } else {
            this.mimeType = null;
            this.description = null;
            this.isBinary = false;
            this.fileSize = -1;
            this.ext = null;
        }
    }

    public String printToTree() {
        return treePrefix + fileName + treeSuffix + "\n";
    }

    @Override
    public String toString() {
        return "FileData{" +
                "vf=" + vf +
                '}';
    }

    public String getContents() throws IOException {
        if (contents == null) {
            byte[] data = vf.contentsToByteArray();
            contents = new String(data, vf.getCharset());
            lines = contents.lines().count();
        }
        return contents;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        FileData fileData = (FileData) o;
        return vf.equals(fileData.vf);
    }

    @Override
    public int hashCode() {
        return vf.hashCode();
    }

    public VirtualFile getVf() {
        return vf;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getLocalPath() {
        return localPath;
    }

    public long getFileSize() {
        return fileSize;
    }

    public long getLines() {
        return lines;
    }

    public boolean isBinary() {
        return isBinary;
    }

    public String getDescription() {
        return description;
    }

    public boolean isFolder() {
        return isFolder;
    }

    public String getExt() {
        return ext;
    }
}
