package com.dobest1.boyka;

import com.intellij.openapi.project.Project;
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

public class BoykaAIToolWindowContent {
    private final Project project;  // Added project field
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
    private Gson gson = new Gson();

    private JTextField openAIBaseAddressField;
    private JTextField openAIKeyField;
    private JTextField claudeAddressField;
    private JTextField claudeKeyField;

    private OkHttpClient client = new OkHttpClient();

    public BoykaAIToolWindowContent(Project project, ToolWindow toolWindow) {
        this.project = project;  // Initialize project field
        myToolWindowContent = new JPanel(new BorderLayout());
        tabbedPane = new JBTabbedPane();

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
        aiService = new BoykaAIService(fileTools);
        contextManager = new ContextManager(project);
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

        // Initialize modelSelector
        modelSelector = new ComboBox<>(new String[]{"claude-3.5-sonnet", "gpt-3.5-turbo"}); // Add your actual model names
        modelSelector.addActionListener(e -> updateChatTabTitle());

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        chatPanel.add(chatScrollPane, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        return chatPanel;
    }
    private void updateChatTabTitle() {
        String selectedModel = (String) modelSelector.getSelectedItem();
        if (selectedModel == null) {
            tabbedPane.setTitleAt(0, "聊天 - " + "dobest1");
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
                if (filePath != null) {
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
                    if (selectedFile != null) {
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
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        BoykaAISettings.State settings = BoykaAISettings.getInstance().getState();

        openAIBaseAddressField = new JBTextField(settings.openAIBaseAddress);
        openAIKeyField = new JBTextField(settings.openAIKey);
        claudeAddressField = new JBTextField(settings.claudeAddress);
        claudeKeyField = new JBTextField(settings.claudeKey);

        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        modelSelector = new ComboBox<>(new DefaultComboBoxModel<>());
        refreshModelsButton = new JButton("刷新模型列表");
        modelPanel.add(new JLabel("选择模型:"));
        modelPanel.add(modelSelector);
        modelPanel.add(refreshModelsButton);

        settingsPanel.add(modelPanel, gbc);

        refreshModelsButton.addActionListener(e -> refreshModels());

        settingsPanel.add(new JLabel("OpenAI 基础地址:"), gbc);
        settingsPanel.add(openAIBaseAddressField, gbc);
        settingsPanel.add(new JLabel("OpenAI API 密钥:"), gbc);
        settingsPanel.add(openAIKeyField, gbc);
        settingsPanel.add(new JLabel("Claude 地址:"), gbc);
        settingsPanel.add(claudeAddressField, gbc);
        settingsPanel.add(new JLabel("Claude API 密钥:"), gbc);
        settingsPanel.add(claudeKeyField, gbc);

        JButton saveButton = new JButton("保存设置");
        saveButton.addActionListener(e -> saveSettings());
        settingsPanel.add(saveButton, gbc);

        return settingsPanel;
    }

    private void saveSettings() {
        BoykaAISettings.State settings = BoykaAISettings.getInstance().getState();
        settings.openAIBaseAddress = openAIBaseAddressField.getText();
        settings.openAIKey = openAIKeyField.getText();
        settings.claudeAddress = claudeAddressField.getText();
        settings.claudeKey = claudeKeyField.getText();
        settings.selectedModel = (String) modelSelector.getSelectedItem();
        BoykaAISettings.getInstance().loadState(settings);

        // 更新 aiService 的设置
        aiService.updateSettings(settings);

        JOptionPane.showMessageDialog(myToolWindowContent, "设置已保存", "保存成功", JOptionPane.INFORMATION_MESSAGE);
    }

    public JComponent getContent() {
        return myToolWindowContent;
    }

    private void sendMessage() {
        if (!aiService.validateSettings()) {
            Messages.showErrorDialog(project, "API设置无效。请检查您的OpenAI基础地址和API密钥。", "设置错误");
            return;
        }
        String message = inputField.getText();
        if (!message.isEmpty()) {
            chatHistory.append("你: " + message + "\n");

            ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在获取AI响应") {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    List<Tool> availableTools = createAvailableTools();
                    String context = getContext();
                    String selectedModel = (String) modelSelector.getSelectedItem();
                    String aiResponse = aiService.getAIResponse(message, "OpenAI", availableTools, context, selectedModel);
                    SwingUtilities.invokeLater(() -> {
                        if (aiResponse.startsWith("Error:")) {
                            chatHistory.append("AI: 抱歉，发生了一个错误。\n");
                            chatHistory.append(aiResponse + "\n");
                            Messages.showErrorDialog(project, aiResponse, "AI响应错误");
                        } else {
                            chatHistory.append("AI: " + aiResponse + "\n");
                        }
                        inputField.setText("");
                        updateChatTabTitle();
                    });
                }
            });
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
    private List<Tool> createAvailableTools() {
        List<Tool> tools = new ArrayList<>();
        
        // Create File Tool
        JsonObject createFileSchema = new JsonObject();
        JsonObject createFileProperties = new JsonObject();
        createFileProperties.addProperty("path", "string");
        createFileProperties.addProperty("content", "string");
        createFileSchema.add("properties", createFileProperties);
        createFileSchema.add("required", gson.toJsonTree(new String[]{"path", "content"}));
        tools.add(new Tool("create_file", 
            "在指定路径创具有给定内容的新文件。当需要在项目结构中创建新文件时使用此工具。如果不存在，它将创建所有必要的父目录。如果文件创建成，工具将返回成功消息，如果创建文件时出现问题或文件已存在，则返回错误消息。内容应尽可能完整和有用，包括必要的导入、函数定义和注释。",
            createFileSchema));

        // Edit and Apply Tool
        JsonObject editAndApplySchema = new JsonObject();
        JsonObject editAndApplyProperties = new JsonObject();
        editAndApplyProperties.addProperty("path", "string");
        editAndApplyProperties.addProperty("instructions", "string");
        editAndApplyProperties.addProperty("project_context", "string");
        editAndApplySchema.add("properties", editAndApplyProperties);
        editAndApplySchema.add("required", gson.toJsonTree(new String[]{"path", "instructions", "project_context"}));
        tools.add(new Tool("edit_and_apply", 
            "根据特定指令和详细的项目上下文对文件应用AI驱动的改进。此函数读取文件，使用带有对话历史和全面代码相关项目上下文的AI分批处理它。它生成差异并允许用户在应用更改之前确认。目是保持一致性并防止破坏文件之间的连接。此工具应用需要理解更广泛项目上下文的复杂代码修改。",
            editAndApplySchema));

        // Execute Code Tool
        JsonObject executeCodeSchema = new JsonObject();
        JsonObject executeCodeProperties = new JsonObject();
        executeCodeProperties.addProperty("code", "string");
        executeCodeSchema.add("properties", executeCodeProperties);
        executeCodeSchema.add("required", gson.toJsonTree(new String[]{"code"}));
        tools.add(new Tool("execute_code", 
            "在'code_execution_env'虚拟环境中执行Python代码并返回输出。当需要运行代码并查看其输出或检查错误时使用此工具。所有代码执行都专门在这个隔离环境中进行。该工具将返回执行代码的标准输出、标准错误和返回代码。长时间运行的进程将返回进程ID以便后续管理。",
            executeCodeSchema));

        // Stop Process Tool
        JsonObject stopProcessSchema = new JsonObject();
        JsonObject stopProcessProperties = new JsonObject();
        stopProcessProperties.addProperty("process_id", "string");
        stopProcessSchema.add("properties", stopProcessProperties);
        stopProcessSchema.add("required", gson.toJsonTree(new String[]{"process_id"}));
        tools.add(new Tool("stop_process", 
            "通过其ID停止运行的进程。此工具用于终止由execute_code工具启动的长时间运行的进程。它将尝试优雅地停止进程，但如果必要，可能会强制终。如果进程被停止，工具将返回成消息，如果进程不存在或无法停止，则返回错误消息。",
            stopProcessSchema));

        // Read File Tool
        JsonObject readFileSchema = new JsonObject();
        JsonObject readFileProperties = new JsonObject();
        readFileProperties.addProperty("path", "string");
        readFileSchema.add("properties", readFileProperties);
        readFileSchema.add("required", gson.toJsonTree(new String[]{"path"}));
        tools.add(new Tool("read_file", 
            "读取指定路径的文件内容。当需要检查现有文件的内容时使用此工具。它将返回文件的全部内容作为字符串。如果文件不存在或无法读取，将返回适当的错误消息。",
            readFileSchema));

        // Read Multiple Files Tool
        JsonObject readMultipleFilesSchema = new JsonObject();
        JsonObject readMultipleFilesProperties = new JsonObject();
        readMultipleFilesProperties.add("paths", gson.toJsonTree(new JsonArray()));
        readMultipleFilesSchema.add("properties", readMultipleFilesProperties);
        readMultipleFilesSchema.add("required", gson.toJsonTree(new String[]{"paths"}));
        tools.add(new Tool("read_multiple_files", 
            "读取指定路径的多个文件的内容。当需一次检查多个现有文件的容时使用此工具。它将返回读取每个文件的状态，并将成��读取的文件内容存储在系统提示中。如果文件不存在或无法读取，将为该文件返回适当的错误消息。",
            readMultipleFilesSchema));

        // List Files Tool
        JsonObject listFilesSchema = new JsonObject();
        JsonObject listFilesProperties = new JsonObject();
        listFilesProperties.addProperty("path", "string");
        listFilesSchema.add("properties", listFilesProperties);
        tools.add(new Tool("list_files", 
            "列出指定文件夹中的所有文件和目录。当需要查看目录内容时使用此工具。它将返回指定路径中所有文件和子目录的列表。如果目录不存在或无法读取，将返回适当的错误消息。",
            listFilesSchema));

        return tools;
    }

    private void refreshModels() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在刷新模型列表") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                List<String> models = fetchAvailableModels();
                SwingUtilities.invokeLater(() -> {
                    DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) modelSelector.getModel();
                    model.removeAllElements();
                    for (String modelName : models) {
                        model.addElement(modelName);
                    }
                    if (!models.isEmpty()) {
                        modelSelector.setSelectedItem(models.get(0));
                        updateChatTabTitle();  // Update the chat tab title after refreshing models
                    }
                });
            }
        });
    }

    private List<String> fetchAvailableModels() {
        BoykaAISettings.State settings = BoykaAISettings.getInstance().getState();
        List<String> modelList = new ArrayList<>();
        String url = settings.openAIBaseAddress + "v1/models"; // 使用配置中的地址

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + settings.openAIKey) // 使用 API 密钥
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
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
}