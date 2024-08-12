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

public class BoykaAIToolWindowContent {
    private final Project project;  // Added project field
    private JPanel myToolWindowContent;
    private JBTabbedPane tabbedPane;
    private JBTextArea chatHistory;
    private JBTextField inputField;
    private JButton sendButton;
    private ComboBox<String> modelSelector;
    private BoykaAIFileTools fileTools;
    private BoykaAIService aiService;
    private ContextManager contextManager;
    private JList<String> contextFilesList;
    private DefaultListModel<String> contextFilesModel;
    private JBTextField contextSearchField;
    private Gson gson = new Gson();

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
        JBScrollPane chatScrollPane = new JBScrollPane(chatHistory);

        inputField = new JBTextField();
        sendButton = new JButton("发送");  // Replace BoykaAIBundle.message() call
        sendButton.addActionListener(e -> sendMessage());

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        chatPanel.add(chatScrollPane, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        return chatPanel;
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

        JTextField openAIAddressField = new JBTextField();
        JTextField openAIKeyField = new JBTextField();
        JTextField claudeAddressField = new JBTextField();
        JTextField claudeKeyField = new JBTextField();
        modelSelector = new ComboBox<>(new String[]{"OpenAI", "Claude"});

        settingsPanel.add(new JLabel("OpenAI 地址:"), gbc);
        settingsPanel.add(openAIAddressField, gbc);
        settingsPanel.add(new JLabel("OpenAI API 密钥:"), gbc);
        settingsPanel.add(openAIKeyField, gbc);
        settingsPanel.add(new JLabel("Claude 地址:"), gbc);
        settingsPanel.add(claudeAddressField, gbc);
        settingsPanel.add(new JLabel("Claude API 密钥:"), gbc);
        settingsPanel.add(claudeKeyField, gbc);
        settingsPanel.add(new JLabel("选择模型:"), gbc);
        settingsPanel.add(modelSelector, gbc);

        JButton saveButton = new JButton("保存设置");
        settingsPanel.add(saveButton, gbc);

        return settingsPanel;
    }

    public JComponent getContent() {
        return myToolWindowContent;
    }

    private void sendMessage() {
        String message = inputField.getText();
        if (!message.isEmpty()) {
            chatHistory.append("你: " + message + "\n");
            
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在理AI响应") {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    indicator.setIndeterminate(true);
                    List<Tool> availableTools = createAvailableTools();
                    String context = getContext();
                    String aiResponse = aiService.getAIResponse(message, (String) modelSelector.getSelectedItem(), availableTools, context);
                    SwingUtilities.invokeLater(() -> {
                        chatHistory.append("AI: " + aiResponse + "\n");
                        inputField.setText("");
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
        
        JsonObject createFileSchema = new JsonObject();
        JsonObject createFileProperties = new JsonObject();
        createFileProperties.addProperty("path", "string");
        createFileProperties.addProperty("content", "string");
        createFileSchema.add("properties", createFileProperties);
        createFileSchema.add("required", gson.toJsonTree(new String[]{"path", "content"}));
        tools.add(new Tool("create_file", 
            "在指定路径创建具有给定内容的新文件。如果不存在，它将创建所有必要的父目录。", 
            createFileSchema));

        JsonObject editAndApplySchema = new JsonObject();
        JsonObject editAndApplyProperties = new JsonObject();
        editAndApplyProperties.addProperty("path", "string");
        editAndApplyProperties.addProperty("instructions", "string");
        editAndApplyProperties.addProperty("project_context", "string");
        editAndApplySchema.add("properties", editAndApplyProperties);
        editAndApplySchema.add("required", new JsonArray());
        tools.add(new Tool("edit_and_apply", 
            "根据特定指令和详细的项目上下文对文件应用AI驱动的改进。此函数读取文件，使用带有对话历史和全面代码相关项目上下文的AI分批处理它。它生成差异并允许用户在应用更改之前确认。目标是保持一致性并防止破坏文件之间的连接。此工具应用于需要理解更广泛项目上下文的复杂代码修改。", 
            editAndApplySchema));

        // ... 为其他工具添加类似的代码 ...

        return tools;
    }

    private void showErrorDialog(String message) {
        SwingUtilities.invokeLater(() -> {
            Messages.showErrorDialog(project, message, "Boyka AI Error");
        });
    }
}