package com.github.enerccio.project2llm.settings;

import com.github.enerccio.ide.MyMessageBundle;
import com.github.enerccio.project2llm.processor.FolderProcessor;
import com.github.enerccio.project2llm.processor.FolderProcessorManager;
import com.github.enerccio.project2llm.processor.ProcessorContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.knuddels.jtokkit.api.ModelType;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractSettingsConfigurable implements Configurable {
    private static final Logger LOG = Logger.getInstance(FolderProcessor.class);

    protected JPanel mainPanel;
    protected ComboBox<String> profileComboBox;
    protected JButton createBtn;
    protected JButton copyBtn;
    protected JButton deleteBtn;

    // Fields of ProcessorContext
    protected ComboBox<ModelType> modelTypeComboBox;
    protected JBTextField suffixField;
    protected JBTextField maxFileSizeField;
    protected JBTextField treeBranchField;
    protected JBTextField treeLeafField;
    protected JBTextField separatorField;
    protected JBTextField treeSpacingField;
    protected JBTextField treeNextField;

    // Buttons for editing templates
    protected JButton topHeaderBtn;
    protected JButton metaInfoBtn;
    protected JButton treeTemplateBtn;
    protected JButton sourceFileBtn;
    protected JButton sourceTooLargeBtn;
    protected JButton binaryFileBtn;

    // Local clone of profiles for cancel/apply logic
    protected Map<String, ProcessorContext> localProfiles = new HashMap<>();
    protected String localActiveProfile;

    // Current working profile selected in settings UI
    protected String currentSelectedProfile;

    protected abstract String getSettingsActiveProfileName();
    protected abstract Map<String, ProcessorContext> getSettingsProfiles();
    protected abstract void saveSettings(String activeProfile, Map<String, ProcessorContext> profiles);
    protected abstract void updateContexts();

    protected ProcessorContext getNewProfileBaseContext() {
        AppSettings appSettings = AppSettings.getInstance();
        if (appSettings != null) {
            String activeProfile = appSettings.getActiveProfileName();
            if (appSettings.getProfiles().containsKey(activeProfile)) {
                return cloneContext(appSettings.getProfiles().get(activeProfile));
            }
        }
        return new ProcessorContext();
    }

    @Override
    public @Nullable JComponent createComponent() {
        profileComboBox = new ComboBox<>();
        createBtn = new JButton(MyMessageBundle.message("settings.create.new"));
        copyBtn = new JButton(MyMessageBundle.message("settings.copy"));
        deleteBtn = new JButton(MyMessageBundle.message("settings.delete"));

        modelTypeComboBox = new ComboBox<>(ModelType.values());
        suffixField = new JBTextField();
        maxFileSizeField = new JBTextField();
        treeBranchField = new JBTextField();
        treeLeafField = new JBTextField();
        separatorField = new JBTextField();
        treeSpacingField = new JBTextField();
        treeNextField = new JBTextField();

        topHeaderBtn = new JButton(MyMessageBundle.message("settings.edit.top.header"));
        metaInfoBtn = new JButton(MyMessageBundle.message("settings.edit.metadata"));
        treeTemplateBtn = new JButton(MyMessageBundle.message("settings.edit.tree"));
        sourceFileBtn = new JButton(MyMessageBundle.message("settings.edit.source"));
        sourceTooLargeBtn = new JButton(MyMessageBundle.message("settings.edit.source.too.large"));
        binaryFileBtn = new JButton(MyMessageBundle.message("settings.edit.binary"));

        // Profile Selection Action
        profileComboBox.addActionListener(e -> {
            String selected = (String) profileComboBox.getSelectedItem();
            if (selected != null && !selected.equals(currentSelectedProfile)) {
                saveUiToProfile(currentSelectedProfile);
                currentSelectedProfile = selected;
                loadProfileToUi(selected);
            }
        });

        // Create New Profile
        createBtn.addActionListener(e -> {
            String name = Messages.showInputDialog(mainPanel, MyMessageBundle.message("settings.enter.new.profile.name"), MyMessageBundle.message("settings.create.profile"), Messages.getQuestionIcon());
            if (name != null && !name.trim().isEmpty()) {
                name = name.trim();
                if (localProfiles.containsKey(name)) {
                    Messages.showErrorDialog(mainPanel, MyMessageBundle.message("settings.profile.already.exists"), MyMessageBundle.message("settings.error"));
                    return;
                }
                saveUiToProfile(currentSelectedProfile);
                localProfiles.put(name, getNewProfileBaseContext());
                updateProfileComboBox(name);
            }
        });

        // Copy Profile
        copyBtn.addActionListener(e -> {
            String name = Messages.showInputDialog(mainPanel, MyMessageBundle.message("settings.enter.name.for.copy"), MyMessageBundle.message("settings.copy.profile"), Messages.getQuestionIcon());
            if (name != null && !name.trim().isEmpty()) {
                name = name.trim();
                if (localProfiles.containsKey(name)) {
                    Messages.showErrorDialog(mainPanel, MyMessageBundle.message("settings.profile.already.exists"), MyMessageBundle.message("settings.error"));
                    return;
                }
                saveUiToProfile(currentSelectedProfile);
                ProcessorContext original = localProfiles.get(currentSelectedProfile);
                ProcessorContext copy = cloneContext(original);
                localProfiles.put(name, copy);
                updateProfileComboBox(name);
            }
        });

        // Delete Profile
        deleteBtn.addActionListener(e -> {
            if (localProfiles.size() <= 1) {
                Messages.showErrorDialog(mainPanel, MyMessageBundle.message("settings.cannot.delete.only.profile"), MyMessageBundle.message("settings.error"));
                return;
            }
            int result = Messages.showYesNoDialog(mainPanel, MyMessageBundle.message("settings.delete.confirm", currentSelectedProfile), MyMessageBundle.message("settings.delete.profile"), Messages.getQuestionIcon());
            if (result == Messages.YES) {
                localProfiles.remove(currentSelectedProfile);
                String nextProfile = localProfiles.keySet().iterator().next();
                currentSelectedProfile = null; // Prevent saving to deleted
                updateProfileComboBox(nextProfile);
            }
        });

        // Templates dialog actions
        topHeaderBtn.addActionListener(e -> openTemplateDialog("Top Header Template", "topHeaderTemplate", List.of()));
        metaInfoBtn.addActionListener(e -> openTemplateDialog("Metadata Template", "metaInfoTemplate",
                List.of("${product}", "${projectName}", "${targetContext}", "${sdk}", "${detectedEcosystem}", "${fileCount}", "${totalSizeKb}", "${tokenCount}", "${hasModuleRoots}", "${modules}")));
        treeTemplateBtn.addActionListener(e -> openTemplateDialog("Tree Structure Template", "treeTemplate", List.of("${tree}")));
        sourceFileBtn.addActionListener(e -> openTemplateDialog("Source File Template", "sourceFileTemplate",
                List.of("${file}", "${type}", "${content}", "${lineCount}", "${extension}")));
        sourceTooLargeBtn.addActionListener(e -> openTemplateDialog("Source Too Large Template", "sourceTooLargeTemplate",
                List.of("${file}", "${type}", "${fileSizeMb}", "${maxFileSize}")));
        binaryFileBtn.addActionListener(e -> openTemplateDialog("Binary File Template", "binaryFile",
                List.of("${file}", "${type}", "${description}", "${fileSizeMb}")));

        JPanel profilePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        profilePanel.add(profileComboBox);
        profilePanel.add(createBtn);
        profilePanel.add(copyBtn);
        profilePanel.add(deleteBtn);

        mainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel(MyMessageBundle.message("settings.active.profile")), profilePanel)
                .addSeparator()
                .addLabeledComponent(MyMessageBundle.message("settings.model.type"), modelTypeComboBox)
                .addLabeledComponent(MyMessageBundle.message("settings.file.suffix"), suffixField)
                .addLabeledComponent(MyMessageBundle.message("settings.max.file.size"), maxFileSizeField)
                .addLabeledComponent(MyMessageBundle.message("settings.tree.branch"), treeBranchField)
                .addLabeledComponent(MyMessageBundle.message("settings.tree.leaf"), treeLeafField)
                .addLabeledComponent(MyMessageBundle.message("settings.path.separator"), separatorField)
                .addLabeledComponent(MyMessageBundle.message("settings.tree.spacing"), treeSpacingField)
                .addLabeledComponent(MyMessageBundle.message("settings.tree.vertical"), treeNextField)
                .addSeparator()
                .addComponent(topHeaderBtn)
                .addComponent(metaInfoBtn)
                .addComponent(treeTemplateBtn)
                .addComponent(sourceFileBtn)
                .addComponent(sourceTooLargeBtn)
                .addComponent(binaryFileBtn)
                .getPanel();

        return mainPanel;
    }

    private void openTemplateDialog(String title, String fieldName, List<String> velocityFields) {
        saveUiToProfile(currentSelectedProfile);
        ProcessorContext context = localProfiles.get(currentSelectedProfile);
        if (context == null) return;

        String initialVal = "";
        try {
            initialVal = (String) ProcessorContext.class.getMethod("get" + capitalize(fieldName)).invoke(context);
        } catch (Exception ignored) {}

        TemplateEditDialog dialog = new TemplateEditDialog(mainPanel, title, initialVal, velocityFields);
        if (dialog.showAndGet()) {
            String updatedContent = dialog.getTemplateContent();
            try {
                ProcessorContext.class.getMethod("set" + capitalize(fieldName), String.class).invoke(context, updatedContent);
            } catch (Exception ignored) {}
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    protected void updateProfileComboBox(String selectItem) {
        profileComboBox.removeAllItems();
        for (String key : localProfiles.keySet()) {
            profileComboBox.addItem(key);
        }
        profileComboBox.setSelectedItem(selectItem);
        currentSelectedProfile = selectItem;
        loadProfileToUi(selectItem);
    }

    protected void loadProfileToUi(String profileName) {
        ProcessorContext context = localProfiles.get(profileName);
        if (context == null) return;

        modelTypeComboBox.setSelectedItem(context.getModelType());
        suffixField.setText(context.getSuffix());
        maxFileSizeField.setText(String.valueOf(context.getMaxFileSize()));
        treeBranchField.setText(context.getTreeBranch());
        treeLeafField.setText(context.getTreeLeaf());
        separatorField.setText(context.getSeparator());
        treeSpacingField.setText(context.getTreeSpacing());
        treeNextField.setText(context.getTreeNext());
    }

    protected void saveUiToProfile(String profileName) {
        if (profileName == null) return;
        ProcessorContext context = localProfiles.get(profileName);
        if (context == null) return;

        context.setModelType((ModelType) modelTypeComboBox.getSelectedItem());
        context.setSuffix(suffixField.getText());
        try {
            context.setMaxFileSize(Long.parseLong(maxFileSizeField.getText()));
        } catch (NumberFormatException ignored) {}
        context.setTreeBranch(treeBranchField.getText());
        context.setTreeLeaf(treeLeafField.getText());
        context.setSeparator(separatorField.getText());
        context.setTreeSpacing(treeSpacingField.getText());
        context.setTreeNext(treeNextField.getText());
    }

    @Override
    public boolean isModified() {
        if (!getSettingsActiveProfileName().equals(profileComboBox.getSelectedItem())) return true;

        saveUiToProfile(currentSelectedProfile);
        if (getSettingsProfiles().size() != localProfiles.size()) return true;

        for (Map.Entry<String, ProcessorContext> entry : localProfiles.entrySet()) {
            ProcessorContext original = getSettingsProfiles().get(entry.getKey());
            if (original == null) return true;
            if (!areContextsEqual(original, entry.getValue())) return true;
        }

        return false;
    }

    @Override
    public void apply() {
        saveUiToProfile(currentSelectedProfile);

        Map<String, ProcessorContext> clonedMap = new HashMap<>();
        for (Map.Entry<String, ProcessorContext> entry : localProfiles.entrySet()) {
            clonedMap.put(entry.getKey(), cloneContext(entry.getValue()));
        }

        saveSettings((String) profileComboBox.getSelectedItem(), clonedMap);

        updateContexts();
    }

    @Override
    public void reset() {
        localActiveProfile = getSettingsActiveProfileName();
        localProfiles.clear();
        for (Map.Entry<String, ProcessorContext> entry : getSettingsProfiles().entrySet()) {
            localProfiles.put(entry.getKey(), cloneContext(entry.getValue()));
        }
        updateProfileComboBox(localActiveProfile);
    }

    protected ProcessorContext cloneContext(ProcessorContext src) {
        ProcessorContext clone = new ProcessorContext();
        clone.setModelType(src.getModelType());
        clone.setSuffix(src.getSuffix());
        clone.setMaxFileSize(src.getMaxFileSize());
        clone.setTreeBranch(src.getTreeBranch());
        clone.setTreeLeaf(src.getTreeLeaf());
        clone.setSeparator(src.getSeparator());
        clone.setTreeSpacing(src.getTreeSpacing());
        clone.setTreeNext(src.getTreeNext());
        clone.setTopHeaderTemplate(src.getTopHeaderTemplate());
        clone.setMetaInfoTemplate(src.getMetaInfoTemplate());
        clone.setTreeTemplate(src.getTreeTemplate());
        clone.setSourceFileTemplate(src.getSourceFileTemplate());
        clone.setSourceTooLargeTemplate(src.getSourceTooLargeTemplate());
        clone.setBinaryFile(src.getBinaryFile());
        return clone;
    }

    protected boolean areContextsEqual(ProcessorContext c1, ProcessorContext c2) {
        return c1.getModelType() == c2.getModelType() &&
                c1.getSuffix().equals(c2.getSuffix()) &&
                c1.getMaxFileSize() == c2.getMaxFileSize() &&
                c1.getTreeBranch().equals(c2.getTreeBranch()) &&
                c1.getTreeLeaf().equals(c2.getTreeLeaf()) &&
                c1.getSeparator().equals(c2.getSeparator()) &&
                c1.getTreeSpacing().equals(c2.getTreeSpacing()) &&
                c1.getTreeNext().equals(c2.getTreeNext()) &&
                c1.getTopHeaderTemplate().equals(c2.getTopHeaderTemplate()) &&
                c1.getMetaInfoTemplate().equals(c2.getMetaInfoTemplate()) &&
                c1.getTreeTemplate().equals(c2.getTreeTemplate()) &&
                c1.getSourceFileTemplate().equals(c2.getSourceFileTemplate()) &&
                c1.getSourceTooLargeTemplate().equals(c2.getSourceTooLargeTemplate()) &&
                c1.getBinaryFile().equals(c2.getBinaryFile());
    }
}