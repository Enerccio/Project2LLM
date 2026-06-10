package com.github.enerccio.project2llm.processor;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

public class ModuleInfo {

    private final Module module;
    private final VirtualFile moduleRoot;
    private final VirtualFile projectRoot;
    private final String separator;

    public ModuleInfo(Module module, VirtualFile moduleRoot, VirtualFile projectRoot, String separator) {
        this.module = module;
        this.moduleRoot = moduleRoot;
        this.projectRoot = projectRoot;
        this.separator = separator;
    }

    public String getName() {
        return module.getName();
    }

    public String getModuleRoot() {
        String relativePath = VfsUtil.getRelativePath(moduleRoot, projectRoot, separator.charAt(0));
        return relativePath.isEmpty() ? separator : relativePath;
    }

}
