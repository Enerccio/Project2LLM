package com.github.enerccio.project2llm.actions;

import com.github.enerccio.project2llm.processor.FolderProcessorManager;
import com.github.enerccio.project2llm.processor.PayloadDescriptor;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CopyPayloadAction extends AnAction {
    private static final Logger LOG = Logger.getInstance(CopyPayloadAction.class);

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile[] virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

        if (project == null || virtualFiles == null || virtualFiles.length == 0) return;

        List<File> ioFiles = new ArrayList<>();
        for (VirtualFile vf : virtualFiles) {
            ioFiles.add(new File(vf.getPath()));
        }

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Generating LLM Payload", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);

                PayloadDescriptor descriptor = FolderProcessorManager.getInstance(project)
                        .createFolderContext(project, ioFiles);

                if (descriptor != null && descriptor.tempFile().exists()) {
                    try {
                        String payloadContent = Files.readString(descriptor.tempFile().toPath());

                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(() -> {
                            CopyPasteManager.getInstance().setContents(new StringSelection(payloadContent));
                            showSuccessNotification(project, descriptor);
                        });
                    } catch (Exception ex) {
                        LOG.error(ex);
                    }
                }
            }
        });
    }

    private void showSuccessNotification(Project project, PayloadDescriptor descriptor) {
        double sizeKb = descriptor.totalSizeBytes() / 1024.0;
        String physicalSizeString = sizeKb > 1024.0
                ? String.format(Locale.US, "%.2f MB", sizeKb / 1024.0)
                : String.format(Locale.US, "%.2f KB", sizeKb);

        String successMessage = String.format(
                "Packed %d files (%s) | %,d tokens copied to clipboard.",
                descriptor.fileCount(), physicalSizeString, descriptor.tokenCount());

        NotificationGroupManager.getInstance()
                .getNotificationGroup("Project2LLM Notifications")
                .createNotification("LLM Payload Copied", successMessage, NotificationType.INFORMATION)
                .notify(project);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        e.getPresentation().setVisible(project != null);
        e.getPresentation().setEnabled(project != null && !DumbService.getInstance(project).isDumb());
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}