package com.dobest1.boyka;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.*;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class BoykaAIToolWindowContent implements ContextManager.ContextChangeListener {
    private Project project;  // Added project field
    private JPanel myToolWindowContent;
    private JBTabbedPane tabbedPane;
    private JBTextArea chatHistory;
    private JBTextField inputField;
    private JButton sendButton;
    private ComboBox<String> modelSelector;
    private JButton refreshModelsButton;
    private BoykaAIFileTools fileTools;
    private BoykaAIService aiService;
    private ContextManager contextManager;
    private JList<String> contextFilesList;
    private DefaultListModel<String> contextFilesModel;
    private JBTextField contextSearchField;
    private JButton continueButton;
    private Gson gson = new Gson();
    private JButton clearButton;

    private JTextField openAIBaseAddressField;
    private JTextField openAIKeyField;
    private JTextField claudeAddressField;
    private JTextField claudeKeyField;
    private JButton autoButton;
    private int remainingAutoRepeatCount;
    private OkHttpClient client = new OkHttpClient();

    public BoykaAIToolWindowContent(Project project, ToolWindow toolWindow) {
        try {
            this.project = project;  // Initialize project field
            myToolWindowContent = new JPanel(new BorderLayout());
            tabbedPane = new JBTabbedPane();
            this.contextManager = ContextManager.getInstance(project);
            this.contextManager.addContextChangeListener(this);
            // Chat tab
            JPanel chatPanel = createChatPanel();
            tabbedPane.addTab("聊天", chatPanel);

            // Context tab
            JPanel contextPanel = createContextPanel(project);
            tabbedPane.addTab("上下文", contextPanel);

            // Settings tab
            JPanel settingsPanel = createSettingsPanel();
            tabbedPane.addTab("设置", settingsPanel);

            myToolWindowContent.add(tabbedPane, BorderLayout.CENTER);
            fileTools = new BoykaAIFileTools(project);
            aiService = new BoykaAIService(fileTools, contextManager);  // 传入两个参数

        } catch (Exception e) {
            BoykaAILogger.error("Error creating BoykaAI tool window content", e);
        }
    }

    private void sendContinueMessage() {
        if (inputField.getText().isEmpty()) {
            inputField.setText("继续");
        }
        sendMessage();
    }


    @Override
    public void onContextChanged() {
        BoykaAILogger.info("contextFilesModel Context files changed");
        SwingUtilities.invokeLater(() -> {
            contextFilesModel.clear();
            BoykaAILogger.info("Adding file to contextFilesModel: " + contextManager.getContextFiles().size());
            for (String filePath : contextManager.getContextFiles()) {
                BoykaAILogger.info("Adding file to contextFilesModel: " + filePath);
                contextFilesModel.addElement(filePath);
            }
        });
    }


    private JPanel createChatPanel() {
        JPanel chatPanel = new JPanel(new BorderLayout());

        chatHistory = new JBTextArea();
        chatHistory.setEditable(false);
        chatHistory.setLineWrap(true);
        JBScrollPane chatScrollPane = new JBScrollPane(chatHistory);

        inputField = new JBTextField();
        sendButton = new JButton("发送");
        sendButton.addActionListener(e -> sendMessage());
        continueButton = new JButton("继续");
        continueButton.addActionListener(e -> sendContinueMessage());
        clearButton = new JButton("清空");
        clearButton.addActionListener(e -> clearConversation());
        autoButton = new JButton("自动");
        autoButton.addActionListener(e -> startAutoRepeat());
        BoykaAISettings.State settings = BoykaAISettings.getInstance().getState();
        modelSelector = new ComboBox<>(new String[]{settings.selectedModel != null ? settings.selectedModel : "gpt-3.5-turbo"});
        modelSelector.addActionListener(e -> updateChatTabTitle());
        JPanel inputPanel = new JPanel(new BorderLayout());

        // 创建一个包含输入框的面板，设置为两行高
        JPanel textFieldPanel = new JPanel(new BorderLayout());
        inputField.setPreferredSize(new Dimension(inputField.getPreferredSize().width, inputField.getPreferredSize().height * 2));
        textFieldPanel.add(inputField, BorderLayout.CENTER);

        // 创建按钮面板，使用 FlowLayout 并右对齐
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(clearButton);
        buttonPanel.add(autoButton);
        buttonPanel.add(continueButton);
        buttonPanel.add(sendButton);

        // 将输入框面板和按钮面板添加到输入面板
        inputPanel.add(textFieldPanel, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.SOUTH);
        chatPanel.add(chatScrollPane, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        return chatPanel;
    }
    // 添加这个方法来滚动到底部
    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = ((JScrollPane) chatHistory.getParent().getParent()).getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }
    private void startAutoRepeat() {
        String continueText = inputField.getText();
        BoykaAISettings.State settings = BoykaAISettings.getInstance().getState();
        remainingAutoRepeatCount = settings.autoRepeatCount;
        sendAutoContinueMessage(continueText);
    }

    private void sendAutoContinueMessage(String continueText) {
        if (remainingAutoRepeatCount > 0) {
            if(inputField.getText().equals("继续")||inputField.getText().isEmpty()) {
                inputField.setText("继续");
            }
            sendMessage();
            inputField.setText(continueText);
            remainingAutoRepeatCount--;
        }
    }

    private void updateChatTabTitle() {
        String selectedModel = (String) modelSelector.getSelectedItem();
        if (selectedModel == null) {
            tabbedPane.setTitleAt(0, "聊天");
            return;
        }
        tabbedPane.setTitleAt(0, "聊天 - " + selectedModel);
    }

    private JPanel createContextPanel(Project project) {
        JPanel contextPanel = new JPanel(new BorderLayout());

        contextFilesModel = new DefaultListModel<>();
        contextFilesList = new JBList<>(contextFilesModel);
        JBScrollPane contextScrollPane = new JBScrollPane(contextFilesList);

        JPanel controlPanel = new JPanel(new BorderLayout());
        contextSearchField = new JBTextField();
        JButton addFileButton = new JButton("添加文件");
        JButton removeFileButton = new JButton("移除文件");

        controlPanel.add(contextSearchField, BorderLayout.CENTER);
        controlPanel.add(addFileButton, BorderLayout.EAST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(addFileButton);
        buttonPanel.add(removeFileButton);

        contextPanel.add(contextScrollPane, BorderLayout.CENTER);
        contextPanel.add(controlPanel, BorderLayout.NORTH);
        contextPanel.add(buttonPanel, BorderLayout.SOUTH);

        // 添加文件到上下文
        addFileButton.addActionListener(e -> {
            String query = contextSearchField.getText();
            if (query.startsWith("+")) {
                // 添加外部文件
                String filePath = contextManager.addExternalFile(query.substring(1).trim());
                if (filePath != null && !contextFilesModel.contains(filePath)) {
                    contextFilesModel.addElement(filePath);
                }
            } else {
                // 搜索项目文件
                List<String> results = contextManager.searchProjectFiles(query);
                if (!results.isEmpty()) {
                    String selectedFile = (String) JOptionPane.showInputDialog(
                            contextPanel,
                            "选择文件",
                            "添加到上下文",
                            JOptionPane.PLAIN_MESSAGE,
                            null,
                            results.toArray(),
                            results.get(0));
                    if (selectedFile != null && !contextFilesModel.contains(selectedFile)) {
                        contextManager.addFileToContext(selectedFile);
                        contextFilesModel.addElement(selectedFile);
                    }
                }
            }
        });

        // 从上下文中移除文件
        removeFileButton.addActionListener(e -> {
            int selectedIndex = contextFilesList.getSelectedIndex();
            if (selectedIndex != -1) {
                String filePath = contextFilesModel.get(selectedIndex);
                contextManager.removeFileFromContext(filePath);
                contextFilesModel.remove(selectedIndex);
            }
        });

        return contextPanel;
    }

    private JPanel createSettingsPanel() {
        try {
            BoykaAIConfigurable configurable = new BoykaAIConfigurable();
            JComponent settingsComponent = configurable.createComponent();

            // 创建一个新的 JPanel 来包含设置组件和保存按钮
            JPanel settingsPanel = new JPanel(new BorderLayout());
            settingsPanel.add(settingsComponent, BorderLayout.CENTER);

            // 获取刷新按钮和模型选择器
            refreshModelsButton = configurable.getRefreshModelsButton();
            modelSelector = configurable.getOpenAIModelSelector();
            // 同步两个 modelSelector
            modelSelector.addActionListener(e -> {
                    String selectedModel = (String) modelSelector.getSelectedItem();
                    if (selectedModel != null) {
                        modelSelector.setSelectedItem(selectedModel);
                    }
                });
            // 添加刷新模型列表的功能
            refreshModelsButton.addActionListener(e -> refreshModels());

            // 创建一个面板来容纳保存按钮
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton saveButton = new JButton("保存设置");
            saveButton.addActionListener(e -> saveSettings(configurable));
            buttonPanel.add(saveButton);

            // 将按钮面板添加到设置面板的底部
            settingsPanel.add(buttonPanel, BorderLayout.SOUTH);

            return settingsPanel;
        } catch (Exception e) {
            e.printStackTrace();
            BoykaAILogger.error("Error creating settings panel", e);
            return null;
        }
    }

    private void saveSettings(BoykaAIConfigurable configurable) {
        try {
            configurable.apply();
            BoykaAISettings.State settings = BoykaAISettings.getInstance().getState();
            aiService.updateSettings(settings);
            modelSelector.setSelectedItem(settings.selectedModel);
            updateChatTabTitle();
            JOptionPane.showMessageDialog(myToolWindowContent, "设置已保存", "保存成功", JOptionPane.INFORMATION_MESSAGE);
        } catch (ConfigurationException e) {
            JOptionPane.showMessageDialog(myToolWindowContent, "保存设置失败: " + e.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    public JComponent getContent() {
        return myToolWindowContent;
    }

    private void sendMessage() {
        String message = inputField.getText();
        if (!message.isEmpty()) {
            sendButton.setEnabled(false);
            continueButton.setEnabled(false);
            autoButton.setEnabled(false);
            chatHistory.append("你: " + message + "\n");
            try {
                ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在获取AI响应") {
                    @Override
                    public void run(@NotNull ProgressIndicator indicator) {
                        indicator.setIndeterminate(true);
                        String context = getContext();
                        BoykaAISettings.State settings = BoykaAISettings.getInstance().getState();
                        inputField.setEnabled(false);
                        String aiResponse = aiService.getAIResponse(message, context);
                        inputField.setEnabled(true);
                        fileTools.refreshFileSystem(project.getProjectFilePath());
                        SwingUtilities.invokeLater(() -> {
                            if (aiResponse.startsWith("Error:")) {
                                chatHistory.append("AI: 抱歉，发生了一个错误。\n");
                                chatHistory.append(aiResponse + "\n");
                                Messages.showErrorDialog(project, aiResponse, "AI响应错误");
                            } else {
                                chatHistory.append("AI: " + aiResponse + "\n");
                            }
                            VirtualFileManager.getInstance().refreshWithoutFileWatcher(true);
                            inputField.setText("");
                            scrollToBottom(); // 滚动到底部
                            updateChatTabTitle();
                            if (remainingAutoRepeatCount > 0) {
                                sendAutoContinueMessage(message);
                            }
                        });
                    }
                });

            } catch (Exception e) {
                BoykaAILogger.error("Error sending message", e);
            } finally {
                sendButton.setEnabled(true);
                continueButton.setEnabled(true);
                autoButton.setEnabled(true);
            }

        }
    }

    private String getContext() {
        StringBuilder contextBuilder = new StringBuilder();
        for (String filePath : contextManager.getContextFiles()) {
            contextBuilder.append("File: ").append(filePath).append("\n");
            contextBuilder.append(fileTools.readFile(filePath)).append("\n\n");
        }
        return contextBuilder.toString();
    }

    public static List<Tool> createAvailableTools() {
        List<Tool> tools = new ArrayList<>();

        // Create File Tool
        JsonObject createFileProperties = new JsonObject();
        JsonObject pathProperty = new JsonObject();
        pathProperty.addProperty("type", "string");
        pathProperty.addProperty("description", "指定创建文件的路径");
        createFileProperties.add("path", pathProperty);
        JsonObject contentProperty = new JsonObject();
        contentProperty.addProperty("type", "string");
        contentProperty.addProperty("description", "文件的内容");
        createFileProperties.add("content", contentProperty);
        tools.add(new Tool("create_file",
                "在指定路径创建具有给定内容的新文件。当需要在项目结构中创建新文件时使用此工具。如果不存在，它将创建所有必要的父目录。如果文件创建成功，工具将返回成功消息，如果创建文件时出现问题或文件已存在，则返回错误消息。内容应尽可能完整和有用，包括必要的导入、函数定义和注释。",
                createFileProperties,
                new String[]{"path", "content"}));

        // Edit and Apply Tool
        JsonObject editAndApplyProperties = new JsonObject();
        JsonObject editPathProperty = new JsonObject();
        editPathProperty.addProperty("type", "string");
        editPathProperty.addProperty("description", "要编辑的文件路径");
        editAndApplyProperties.add("path", editPathProperty);
        JsonObject instructionsProperty = new JsonObject();
        instructionsProperty.addProperty("type", "string");
        instructionsProperty.addProperty("description", "编辑指令");
        editAndApplyProperties.add("instructions", instructionsProperty);
        JsonObject projectContextProperty = new JsonObject();
        projectContextProperty.addProperty("type", "string");
        projectContextProperty.addProperty("description", "项目上下文信息");
        editAndApplyProperties.add("project_context", projectContextProperty);
        tools.add(new Tool("edit_and_apply",
                "根据特定指令和详细的项目上下文对文件应用AI驱动的改进。此函数读取文件，使用带有对话历史和全面代码相关项目上下文的AI分批处理它。它生成差异并允许用户在应用更改之前确认。目标是保持一致性并防止破坏文件之间的连接。此工具应用需要理解更广泛项目上下文的复杂代码修改。",
                editAndApplyProperties,
                new String[]{"path", "instructions", "project_context"}));

        // Execute Code Tool
        JsonObject executeCodeProperties = new JsonObject();
        JsonObject codeProperty = new JsonObject();
        codeProperty.addProperty("type", "string");
        codeProperty.addProperty("description", "要执行的Python代码");
        executeCodeProperties.add("code", codeProperty);
        tools.add(new Tool("execute_code",
                "在'code_execution_env'虚拟环境中执行Python代码并返回输出。当需要运行代码并查看其输出或检查错误时使用此工具。所有代码执行都专门在这个隔离环境中进行。该工具将返回执行代码的标准输出、标准错误和返回代码。长时间运行的进程将返回进程ID以便后续管理。",
                executeCodeProperties,
                new String[]{"code"}));

        // Stop Process Tool
        JsonObject stopProcessProperties = new JsonObject();
        JsonObject processIdProperty = new JsonObject();
        processIdProperty.addProperty("type", "string");
        processIdProperty.addProperty("description", "要停止的进程ID");
        stopProcessProperties.add("process_id", processIdProperty);
        tools.add(new Tool("stop_process",
                "通过其ID停止运行的进程。此工具用于终止由execute_code工具启动的长时间运行的进程。它将尝试优雅地停止进程，但如果必要，可能会强制终止。如果进程被停止，工具将返回成功消息，如果进程不存在或无法停止，则返回错误消息。",
                stopProcessProperties,
                new String[]{"process_id"}));

        // Read File Tool
        JsonObject readFileProperties = new JsonObject();
        JsonObject readPathProperty = new JsonObject();
        readPathProperty.addProperty("type", "string");
        readPathProperty.addProperty("description", "要读取的文件路径");
        readFileProperties.add("path", readPathProperty);
        tools.add(new Tool("read_file",
                "读取指定路径的文件内容。当需要检查现有文件的内容时使用此工具。它将返回文件的全部内容作为字符串。如果文件不存在或无法读取，将返回适当的错误消息。",
                readFileProperties,
                new String[]{"path"}));

// Read Multiple Files Tool
//        JsonObject readMultipleFilesSchema = new JsonObject();
//        readMultipleFilesSchema.addProperty("type", "object");
//
//        JsonObject properties = new JsonObject();
//        JsonObject pathsProperty = new JsonObject();
//        pathsProperty.addProperty("type", "array");
//
//        JsonObject items = new JsonObject();
//        items.addProperty("type", "string");
//        pathsProperty.add("items", items);
//
//        pathsProperty.addProperty("description", "要读取的文件路径列表。使用正斜杠(/)作为路径分隔符，即使在Windows系统上也是如此。");
//        properties.add("paths", pathsProperty);
//
//        readMultipleFilesSchema.add("properties", properties);
//        readMultipleFilesSchema.add("required", gson.toJsonTree(new String[]{"paths"}));
//
//        tools.add(new Tool("read_multiple_files",
//                "读取指定路径的多个文件的内容。当需要一次检查多个现有文件的内容时使用此工具。它将返回读取每个文件的状态，并将成功读取的文件内容存储在系统提示中。如果文件不存在或无法读取，将为该文件返回适当的错误消息。",
//                readMultipleFilesSchema,
//                new String[]{"paths"}));
        // List Files Tool
        JsonObject listFilesProperties = new JsonObject();
        JsonObject listPathProperty = new JsonObject();
        listPathProperty.addProperty("type", "string");
        listPathProperty.addProperty("description", "要列出内容的文件夹路径");
        listFilesProperties.add("path", listPathProperty);
        tools.add(new Tool("list_files",
                "列出指定文件夹中的所有文件和目录。当需要查看目录内容时使用此工具。它将返回指定路径中所有文件和子目录的列表。如果目录不存在或无法读取，将返回适当的错误消息。",
                listFilesProperties,
                new String[]{"path"}));

        return tools;
    }

    private void refreshModels() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在刷新模型列表") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                List<String> models = fetchAvailableModels();
                SwingUtilities.invokeLater(() -> {
                    String currentModel = (String) modelSelector.getSelectedItem();
                    DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) modelSelector.getModel();
                    model.removeAllElements();
                    for (String modelName : models) {
                        model.addElement(modelName);
                    }
                    if (!models.isEmpty()) {
                        if (models.contains(currentModel)) {
                            modelSelector.setSelectedItem(currentModel);
                        } else {
                            modelSelector.setSelectedItem(models.get(0));
                        }
                        updateChatTabTitle();
                    }
                });
            }
        });
    }

    private List<String> fetchAvailableModels() {
        BoykaAISettings.State settings = BoykaAISettings.getInstance().getState();
        List<String> modelList = new ArrayList<>();
        String url = settings.openAIBaseAddress; // 使用配置中的地址

        url = url + "models"; // 使用配置中的地址


        BoykaAILogger.info("Fetching models from: " + url);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + settings.openAIKey) // 使用 API 密钥
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                BoykaAILogger.warn("无法获取模型列表: " + response.code() + " " + response.message());
                showErrorDialog("无法获取模型列表: " + response.message());
                return modelList; // 返回空列表
            }

            // 解析 JSON 响应
            JsonObject jsonResponse = gson.fromJson(response.body().string(), JsonObject.class);
            JsonArray models = jsonResponse.getAsJsonArray("data");

            for (int i = 0; i < models.size(); i++) {
                JsonObject model = models.get(i).getAsJsonObject();
                modelList.add(model.get("id").getAsString()); // 提取模型 ID
            }
        } catch (IOException e) {
            showErrorDialog("请求失败: " + e.getMessage());
        }

        return modelList; // 返回模型列表
    }

    private void showErrorDialog(String message) {
        SwingUtilities.invokeLater(() -> {
            Messages.showErrorDialog(project, message, "Boyka AI Error");
        });
    }

    private void clearConversation() {
        aiService.clearConversationHistory();
        chatHistory.setText("");
    }

}