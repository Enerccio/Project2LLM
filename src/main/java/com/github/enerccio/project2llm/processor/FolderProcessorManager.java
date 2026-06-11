package com.github.enerccio.project2llm.processor;

import com.github.enerccio.project2llm.settings.AppSettings;
import com.github.enerccio.project2llm.settings.ProjectSettings;
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
        return ApplicationManager.getApplication().runReadAction((Computable<File>) () -> new FolderProcessor(project, sourceFiles, getContext(project), velocityEngine).process());
    }

    private synchronized ProcessorContext getContext(Project project) {
        ProjectSettings projectSettings = ProjectSettings.getInstance(project);
        if (projectSettings != null) {
            String activeProfile = projectSettings.getActiveProfileName();
            if (projectSettings.getProfiles().containsKey(activeProfile)) {
                return projectSettings.getProfiles().get(activeProfile);
            }
        }
        AppSettings appSettings = AppSettings.getInstance();
        if (appSettings != null) {
            String activeProfile = appSettings.getActiveProfileName();
            if (appSettings.getProfiles().containsKey(activeProfile)) {
                return appSettings.getProfiles().get(activeProfile);
            }
        }
        if (context == null) {
            context = new ProcessorContext();
        }

        return context;
    }

    public void clearContext() {
        context = null;
    }

}
