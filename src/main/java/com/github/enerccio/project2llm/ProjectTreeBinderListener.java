package com.github.enerccio.project2llm;

import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ProjectTreeBinderListener implements ToolWindowManagerListener {
    private static final Logger LOG = Logger.getInstance(ProjectTreeBinderListener.class);

    private final Project project;

    public ProjectTreeBinderListener(Project project) {
        this.project = project;
    }

    @Override
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
        // 1. Locate the main Project Tool Window
        ToolWindow toolWindow = toolWindowManager.getToolWindow("Project");
        if (toolWindow != null && toolWindow.isVisible()) {
            
            // 2. Extract the active visual pane (Packages View, Project View, etc.)
            ProjectView projectView = ProjectView.getInstance(project);
            AbstractProjectViewPane pane = projectView.getCurrentProjectViewPane();
            
            if (pane != null) {
                JTree projectTree = pane.getTree();
                
                // 3. Inject your custom DnD registry override
                if (projectTree != null) {
                    injectRepo2TxtHook(projectTree);
                }
            }
        }
    }

    private void injectRepo2TxtHook(JTree projectTree) {
        LOG.info("Injecting Repo2Txt DnD hook");
        if (Boolean.TRUE.equals(projectTree.getClientProperty("repo2txt.hooked"))) return;

        Object dndSource = projectTree.getClientProperty("DnD Source");
        if (dndSource instanceof com.intellij.ide.dnd.DnDSource && !(dndSource instanceof DnDSourceWrapper)) {
            LOG.info("Hooking Repo2Txt DnD");
            projectTree.putClientProperty("DnD Source", new DnDSourceWrapper(project, (com.intellij.ide.dnd.DnDSource) dndSource));
            projectTree.putClientProperty("repo2txt.hooked", Boolean.TRUE);
        } else {
            LOG.error("Failed to hook Repo2Txt DnD");
        }
    }

}