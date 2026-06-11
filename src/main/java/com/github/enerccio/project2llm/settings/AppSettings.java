package com.github.enerccio.project2llm.settings;

import com.github.enerccio.project2llm.processor.ProcessorContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@State(
    name = "com.github.enerccio.project2llm.settings.AppSettings",
    storages = @Storage("project2llm-app.xml")
)
public class AppSettings implements PersistentStateComponent<AppSettings> {

    private String activeProfileName = "Default";
    private Map<String, ProcessorContext> profiles = new HashMap<>();

    public AppSettings() {
        // Initialize default profile
        profiles.put("Default", new ProcessorContext());
    }

    public static AppSettings getInstance() {
        return ApplicationManager.getApplication().getService(AppSettings.class);
    }

    public String getActiveProfileName() {
        return activeProfileName;
    }

    public void setActiveProfileName(String activeProfileName) {
        this.activeProfileName = activeProfileName;
    }

    public Map<String, ProcessorContext> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<String, ProcessorContext> profiles) {
        this.profiles = profiles;
    }

    @Nullable
    @Override
    public AppSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull AppSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
