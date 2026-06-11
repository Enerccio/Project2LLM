package com.github.enerccio.project2llm.settings;

import com.github.enerccio.ide.MyMessageBundle;
import com.github.enerccio.project2llm.processor.FolderProcessorManager;
import com.github.enerccio.project2llm.processor.ProcessorContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.Nls;

import java.util.Map;

public class AppSettingsConfigurable extends AbstractSettingsConfigurable {

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return MyMessageBundle.message("settings.app.display.name");
    }

    @Override
    protected String getSettingsActiveProfileName() {
        return AppSettings.getInstance().getActiveProfileName();
    }

    @Override
    protected Map<String, ProcessorContext> getSettingsProfiles() {
        return AppSettings.getInstance().getProfiles();
    }

    @Override
    protected void saveSettings(String activeProfile, Map<String, ProcessorContext> profiles) {
        AppSettings settings = AppSettings.getInstance();
        settings.setActiveProfileName(activeProfile);
        settings.setProfiles(profiles);
    }

    @Override
    protected void updateContexts() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            FolderProcessorManager.getInstance(project).clearContext();
        }
    }
}