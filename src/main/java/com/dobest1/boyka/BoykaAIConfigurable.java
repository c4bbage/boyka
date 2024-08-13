package com.dobest1.boyka;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

public class BoykaAIConfigurable implements Configurable {
    private JPanel mainPanel;
    private JBTabbedPane tabbedPane;

    // OpenAI fields
    private JBTextField openAIAddressField;
    private JBTextField openAIKeyField;
    private ComboBox<String> openAIModelSelector;

    // Claude fields
    private JBTextField claudeAddressField;
    private JBTextField claudeKeyField;
    private JBTextField claudeModelField;

    // Common fields
    private JBTextField maxTokensField;
    private JCheckBox enableClaude;
    private JCheckBox enableOpenai;

    private JButton refreshModelsButton;

    @Override
    public JComponent createComponent() {
        if (mainPanel == null) {
            createUIComponents();
        }
        return mainPanel;
    }

    private void createUIComponents() {
        mainPanel = new JPanel(new BorderLayout());
        tabbedPane = new JBTabbedPane();

        // Create OpenAI panel
        JPanel openAIPanel = createOpenAIPanel();
        tabbedPane.addTab("OpenAI", openAIPanel);

        // Create Claude panel
        JPanel claudePanel = createClaudePanel();
        tabbedPane.addTab("Claude", claudePanel);

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Add common controls at the bottom
        JPanel commonPanel = createCommonPanel();
        mainPanel.add(commonPanel, BorderLayout.SOUTH);

        loadSettings();
    }

    private JPanel createOpenAIPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createDefaultGBC();

        panel.add(new JBLabel("OpenAI 地址:"), gbc);
        openAIAddressField = new JBTextField();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(openAIAddressField, gbc);

        gbc.gridwidth = 1;
        panel.add(new JBLabel("OpenAI API 密钥:"), gbc);
        openAIKeyField = new JBTextField();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(openAIKeyField, gbc);

        gbc.gridwidth = 1;
        panel.add(new JBLabel("选择模型:"), gbc);
        openAIModelSelector = new ComboBox<>();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(openAIModelSelector, gbc);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        refreshModelsButton = new JButton("刷新模型列表");
        panel.add(refreshModelsButton, gbc);
        gbc.gridwidth=1;
        panel.add(new JBLabel("启用OpenAI:"), gbc);
        enableOpenai = new JCheckBox();  // 初始化 enableOpenai
        panel.add(enableOpenai, gbc);

        return panel;
    }

    private JPanel createClaudePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createDefaultGBC();

        panel.add(new JBLabel("Claude 地址:"), gbc);
        claudeAddressField = new JBTextField();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(claudeAddressField, gbc);

        gbc.gridwidth = 1;
        panel.add(new JBLabel("Claude API 密钥:"), gbc);
        claudeKeyField = new JBTextField();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(claudeKeyField, gbc);

        gbc.gridwidth = 1;
        panel.add(new JBLabel("Claude 模型:"), gbc);
        claudeModelField = new JBTextField();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(claudeModelField, gbc);
        gbc.gridwidth=1;
        panel.add(new JBLabel("启用Claude:"), gbc);
        enableClaude = new JCheckBox();  // 初始化 enableClaude
        panel.add(enableClaude, gbc);

        return panel;
    }

    private JPanel createCommonPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createDefaultGBC();

        panel.add(new JBLabel("最大 Tokens:"), gbc);
        maxTokensField = new JBTextField();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        panel.add(maxTokensField, gbc);

        return panel;
    }

    private GridBagConstraints createDefaultGBC() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = JBUI.insets(5);
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private void loadSettings() {
        BoykaAISettings.State state = BoykaAISettings.getInstance().getState();
        openAIAddressField.setText(state.openAIBaseAddress);
        openAIKeyField.setText(state.openAIKey);
        claudeAddressField.setText(state.claudeAddress);
        claudeKeyField.setText(state.claudeKey);
        claudeModelField.setText(state.claudeModel);
        maxTokensField.setText(String.valueOf(state.maxTokens));
        enableClaude.setSelected(state.enableClaude);
        enableOpenai.setSelected(state.enableOpenai);
        openAIModelSelector.setSelectedItem(state.selectedModel);
    }

    @Override
    public boolean isModified() {
        BoykaAISettings.State state = BoykaAISettings.getInstance().getState();
        return !openAIAddressField.getText().equals(state.openAIBaseAddress)
                || !openAIKeyField.getText().equals(state.openAIKey)
                || !claudeAddressField.getText().equals(state.claudeAddress)
                || !claudeKeyField.getText().equals(state.claudeKey)
                || !claudeModelField.getText().equals(state.claudeModel)
                || Integer.parseInt(maxTokensField.getText()) != state.maxTokens
                || enableClaude.isSelected() != state.enableClaude
                || enableOpenai.isSelected() != state.enableOpenai
                || !openAIModelSelector.getSelectedItem().equals(state.selectedModel);
    }

    @Override
    public void apply() throws ConfigurationException {
        BoykaAISettings.State state = BoykaAISettings.getInstance().getState();
        state.openAIBaseAddress = openAIAddressField.getText();
        state.openAIKey = openAIKeyField.getText();
        state.claudeAddress = claudeAddressField.getText();
        state.claudeKey = claudeKeyField.getText();
        state.claudeModel = claudeModelField.getText();
        state.maxTokens = Integer.parseInt(maxTokensField.getText());
        state.enableClaude = enableClaude.isSelected();
        state.enableOpenai = enableOpenai.isSelected();
        state.selectedModel = (String) openAIModelSelector.getSelectedItem();
        BoykaAISettings.getInstance().loadState(state);
    }

    @Override
    public void reset() {
        loadSettings();
    }

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "Boyka AI";
    }

    public JButton getRefreshModelsButton() {
        return refreshModelsButton;
    }

    public ComboBox<String> getOpenAIModelSelector() {
        return openAIModelSelector;
    }

}