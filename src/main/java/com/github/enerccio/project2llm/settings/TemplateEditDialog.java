
package com.github.enerccio.project2llm.settings;

import com.github.enerccio.ide.MyMessageBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class TemplateEditDialog extends DialogWrapper {
    private final JTextArea textArea;
    private final List<String> velocityFields;

    protected TemplateEditDialog(Component parent, String templateName, String initialContent, List<String> velocityFields) {
        super(parent, true);
        this.velocityFields = velocityFields;
        this.textArea = new JTextArea(initialContent, 20, 60);
        this.textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        setTitle(MyMessageBundle.message("dialog.edit.template.title", templateName));
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder(MyMessageBundle.message("dialog.template.content")));
        leftPanel.add(new JBScrollPane(textArea), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(BorderFactory.createTitledBorder(MyMessageBundle.message("dialog.available.velocity.fields")));
        JBList<String> list = new JBList<>(velocityFields);
        rightPanel.add(new JBScrollPane(list), BorderLayout.CENTER);
        rightPanel.setPreferredSize(new Dimension(220, -1));

        panel.add(leftPanel, BorderLayout.CENTER);
        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    public String getTemplateContent() {
        return textArea.getText();
    }
}