package com.github.enerccio.project2llm.settings;

import com.github.enerccio.ide.MyMessageBundle;
import com.github.enerccio.project2llm.processor.FolderProcessorManager;
import com.github.enerccio.project2llm.processor.ProcessorContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ProjectSettingsConfigurable extends AbstractSettingsConfigurable {

    private final Project project;

    public ProjectSettingsConfigurable(@NotNull Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return MyMessageBundle.message("settings.project.display.name");
    }

    @Override
    protected String getSettingsActiveProfileName() {
        return ProjectSettings.getInstance(project).getActiveProfileName();
    }

    @Override
    protected Map<String, ProcessorContext> getSettingsProfiles() {
        return ProjectSettings.getInstance(project).getProfiles();
    }

    @Override
    protected void saveSettings(String activeProfile, Map<String, ProcessorContext> profiles) {
        ProjectSettings settings = ProjectSettings.getInstance(project);
        settings.setActiveProfileName(activeProfile);
        settings.setProfiles(profiles);
    }

    @Override
    protected void updateContexts() {
        FolderProcessorManager.getInstance(project).clearContext();
    }
}