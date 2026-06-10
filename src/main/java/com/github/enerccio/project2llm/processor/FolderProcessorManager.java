package com.github.enerccio.project2llm.processor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.apache.velocity.app.VelocityEngine;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

public class FolderProcessorManager {

    private volatile ProcessorContext context;
    private final VelocityEngine velocityEngine;

    public FolderProcessorManager() {
        velocityEngine = new VelocityEngine();
        velocityEngine.init();
    }

    public static FolderProcessorManager getInstance(@NotNull Project project) {
        return project.getService(FolderProcessorManager.class);
    }

    public File createFolderContext(Project project, List<File> sourceFiles) {
        return ApplicationManager.getApplication().runReadAction((Computable<File>) () -> new FolderProcessor(project, sourceFiles, getContext(), velocityEngine).process());
    }

    private synchronized ProcessorContext getContext() {
        if (context == null) {
            // load from settings
            context = new ProcessorContext();
        }

        return context;
    }

    public void clearContext() {
        context = null;
    }

}
