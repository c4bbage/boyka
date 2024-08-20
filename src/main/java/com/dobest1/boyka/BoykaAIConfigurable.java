package com.dobest1.boyka;

import com.intellij.icons.AllIcons;
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
    private JBTextField autoRepeatCountField;

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

        // OpenAI 地址
        JPanel addressLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JBLabel addressLabel = new JBLabel("OpenAI base_url:");
        addressLabelPanel.add(addressLabel);

        JBLabel infoLabel = new JBLabel(AllIcons.General.Information);
        infoLabel.setToolTipText("https://api.openai.com/v1/");
        addressLabelPanel.add(infoLabel);

        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(addressLabelPanel, gbc);

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        openAIAddressField = createConfiguredTextField();
        panel.add(openAIAddressField, gbc);

        // OpenAI API 密钥
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JBLabel("OpenAI API-KEY:"), gbc);

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        openAIKeyField = createConfiguredTextField();
        panel.add(openAIKeyField, gbc);

        // 选择模型
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JBLabel("Select model:"), gbc);

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        openAIModelSelector = new ComboBox<>();
        panel.add(openAIModelSelector, gbc);

        // 刷新模型列表按钮
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        refreshModelsButton = new JButton("Refresh models");
        panel.add(refreshModelsButton, gbc);

        // 启用 OpenAI
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JBLabel("Enable openAI:"), gbc);

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        enableOpenai = new JCheckBox();
        panel.add(enableOpenai, gbc);

        return panel;
    }

    private JPanel createClaudePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createDefaultGBC();


        JPanel addressLabelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        JBLabel addressLabel = new JBLabel("Claude base_url:");
        addressLabelPanel.add(addressLabel);

        JBLabel infoLabel = new JBLabel(AllIcons.General.Information);
        infoLabel.setToolTipText("ex: https://api.anthropic.com/v1/");
        addressLabelPanel.add(infoLabel);

        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(addressLabelPanel, gbc);

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        claudeAddressField = createConfiguredTextField();
        panel.add(claudeAddressField, gbc);



        // Claude API 密钥
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JBLabel("Claude API-KEY:"), gbc);

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        claudeKeyField = createConfiguredTextField();
        panel.add(claudeKeyField, gbc);

        // Claude 模型
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JBLabel("Claude model:"), gbc);

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        claudeModelField = createConfiguredTextField();
        panel.add(claudeModelField, gbc);

        // 启用 Claude
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        gbc.fill = GridBagConstraints.NONE;
        panel.add(new JBLabel("Enable claude:"), gbc);

        gbc.gridwidth = GridBagConstraints.REMAINDER;
        enableClaude = new JCheckBox();
        panel.add(enableClaude, gbc);

        return panel;
    }

    private JBTextField createConfiguredTextField() {
        JBTextField textField = new JBTextField();
        textField.setPreferredSize(new Dimension(300, textField.getPreferredSize().height));
        return textField;
    }



    private JPanel createCommonPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = createDefaultGBC();

        // 第一行：最大 Tokens
        gbc.gridwidth = 1;
        panel.add(new JBLabel("Max tokens:"), gbc);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        maxTokensField = new JBTextField();
        panel.add(maxTokensField, gbc);

        // 第二行：自动重复次数
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        panel.add(new JBLabel("Auto repeat count:"), gbc);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        autoRepeatCountField = new JBTextField();
        panel.add(autoRepeatCountField, gbc);

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
        autoRepeatCountField.setText(String.valueOf(state.autoRepeatCount));
        if (state.selectedModel != null && !state.selectedModel.isEmpty()) {
            DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) openAIModelSelector.getModel();
            model.addElement(state.selectedModel);
            openAIModelSelector.setSelectedItem(state.selectedModel);
        }
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
                || !openAIModelSelector.getSelectedItem().equals(state.selectedModel)
                ||  !autoRepeatCountField.getText().equals(String.valueOf(state.autoRepeatCount));
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
        state.autoRepeatCount = Integer.parseInt(autoRepeatCountField.getText());
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