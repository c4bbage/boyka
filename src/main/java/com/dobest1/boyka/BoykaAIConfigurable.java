package com.dobest1.boyka;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
// 这个文件是用于配置Boyka AI助手的设置界面
// 它实现了IntelliJ IDEA的Configurable接口，用于创建和管理插件的配置选项
// 主要功能包括：
// 1. 创建配置界面，包含OpenAI和Claude的地址和API密钥输入字段
// 2. 提供模型选择器，用于选择使用OpenAI还是Claude模型
// 3. 加载和保存用户设置
// 4. 检测设置是否被修改
// 5. 应用用户修改的设置
public class BoykaAIConfigurable implements Configurable {
    private JBTextField openAIAddressField;
    private JBTextField openAIKeyField;
    private JBTextField claudeAddressField;
    private JBTextField claudeKeyField;
    private ComboBox<String> modelSelector;


    @Override
    public String getDisplayName() {
        return "Boyka AI 助手配置";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        openAIAddressField = new JBTextField();
        openAIKeyField = new JBTextField();
        claudeAddressField = new JBTextField();
        claudeKeyField = new JBTextField();
        modelSelector = new ComboBox<>(new String[]{"OpenAI", "Claude"});

        loadSettings();

        return FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("OpenAI 地址: "), openAIAddressField)
                .addLabeledComponent(new JBLabel("OpenAI API 密钥: "), openAIKeyField)
                .addLabeledComponent(new JBLabel("Claude 地址: "), claudeAddressField)
                .addLabeledComponent(new JBLabel("Claude API 密钥: "), claudeKeyField)
                .addLabeledComponent(new JBLabel("厂商: "), modelSelector)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    private void loadSettings() {
        BoykaAISettings.State state = BoykaAISettings.getInstance().getState();
        openAIAddressField.setText(state.openAIBaseAddress);
        openAIKeyField.setText(state.openAIKey);
        claudeAddressField.setText(state.claudeAddress);
        claudeKeyField.setText(state.claudeKey);
        modelSelector.setSelectedItem(state.selectedModel);
    }

    @Override
    public boolean isModified() {
        BoykaAISettings.State state = BoykaAISettings.getInstance().getState();
        return !openAIAddressField.getText().equals(state.openAIBaseAddress) ||
                !openAIKeyField.getText().equals(state.openAIKey) ||
                !claudeAddressField.getText().equals(state.claudeAddress) ||
                !claudeKeyField.getText().equals(state.claudeKey) ||
                !modelSelector.getSelectedItem().equals(state.selectedModel);
    }

    @Override
    public void apply() throws ConfigurationException {
        BoykaAISettings.State state = BoykaAISettings.getInstance().getState();
        state.openAIBaseAddress = openAIAddressField.getText();
        state.openAIKey = openAIKeyField.getText();
        state.claudeAddress = claudeAddressField.getText();
        state.claudeKey = claudeKeyField.getText();
        state.selectedModel = (String) modelSelector.getSelectedItem();
        BoykaAISettings.getInstance().loadState(state);
    }

    @Override
    public void reset() {
        loadSettings();
    }
}