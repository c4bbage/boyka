package com.dobest1.boyka;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import javax.swing.*;
import javax.swing.text.*;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnhancedChatPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(EnhancedChatPanel.class.getName());

    private final JTextPane chatHistory;
    private final JTextArea inputField;
    private  JButton sendButton;
    private  JButton continueButton;
    private  JButton clearButton;
    private  JButton autoButton;
    private  JComboBox<String> modelSelector;
    private  JToggleButton themeToggle;
    private final Project project;

    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private boolean isDarkTheme = false;

    public EnhancedChatPanel(Project project) {
        this.project = project;
        setLayout(new BorderLayout());

        // 创建分割面板
        JBSplitter splitter = new JBSplitter(true, 0.7f);
        add(splitter, BorderLayout.CENTER);

        // 聊天历史区域
        chatHistory = createChatHistoryPane();
        JBScrollPane chatScrollPane = new JBScrollPane(chatHistory);
        splitter.setFirstComponent(chatScrollPane);

        // 输入区域
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputField = createInputField();
        JScrollPane inputScrollPane = new JBScrollPane(inputField);
        inputPanel.add(inputScrollPane, BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = createButtonPanel();
        inputPanel.add(buttonPanel, BorderLayout.SOUTH);

        splitter.setSecondComponent(inputPanel);

        setupKeyBindings();
        setupTheme();
    }

    private JTextPane createChatHistoryPane() {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setContentType("text/html");
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        pane.setFont(JBUI.Fonts.create("Segoe UI", 14));
        return pane;
    }

    private JTextArea createInputField() {
        JTextArea area = new JTextArea();
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(JBUI.Fonts.create("Segoe UI", 14));
        return area;
    }

    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        clearButton = new JButton("清空");
        autoButton = new JButton("自动");
        continueButton = new JButton("继续");
        sendButton = new JButton("发送");
        modelSelector = new JComboBox<>(new String[]{"gpt-3.5-turbo", "gpt-4"});
        themeToggle = new JToggleButton("切换主题");

        clearButton.addActionListener(this::clearConversation);
        autoButton.addActionListener(this::startAutoRepeat);
        continueButton.addActionListener(this::sendContinueMessage);
        sendButton.addActionListener(this::sendMessage);
        themeToggle.addActionListener(e -> toggleTheme());

        panel.add(clearButton);
        panel.add(autoButton);
        panel.add(continueButton);
        panel.add(sendButton);
        panel.add(modelSelector);
        panel.add(themeToggle);

        return panel;
    }

    private void setupKeyBindings() {
        InputMap inputMap = inputField.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = inputField.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "sendMessage");
        actionMap.put("sendMessage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage(e);
            }
        });
    }

    private void setupTheme() {
        updateTheme();
    }

    private void toggleTheme() {
        isDarkTheme = !isDarkTheme;
        updateTheme();
    }

    private void updateTheme() {
        Color bgColor = isDarkTheme ? new Color(43, 43, 43) : Color.WHITE;
        Color fgColor = isDarkTheme ? Color.LIGHT_GRAY : Color.BLACK;

        chatHistory.setBackground(bgColor);
        chatHistory.setForeground(fgColor);
        inputField.setBackground(bgColor);
        inputField.setForeground(fgColor);
        inputField.setCaretColor(fgColor);

        // 更新HTML样式
        updateHtmlStyle();
    }

    private void updateHtmlStyle() {
        String style = String.format(
                "<style>" +
                        "body { background-color: %s; color: %s; }" +
                        ".user-message { background-color: %s; }" +
                        ".ai-message { background-color: %s; }" +
                        "</style>",
                isDarkTheme ? "#2b2b2b" : "#ffffff",
                isDarkTheme ? "#d4d4d4" : "#000000",
                isDarkTheme ? "#4b4b4b" : "#e6f3ff",
                isDarkTheme ? "#3b3b3b" : "#f0f0f0"
        );

        HTMLDocument doc = (HTMLDocument) chatHistory.getDocument();
        Element head = doc.getElement(doc.getDefaultRootElement(), StyleConstants.NameAttribute, HTML.Tag.HEAD);

        try {
            if (head != null) {
                Element styleElement = doc.getElement(head, StyleConstants.NameAttribute, HTML.Tag.STYLE);
                if (styleElement != null) {
                    doc.setInnerHTML(styleElement, style);
                } else {
                    doc.insertBeforeEnd(head, style);
                }
            } else {
                doc.insertBeforeStart(doc.getDefaultRootElement(), "<head>" + style + "</head>");
            }
        } catch (BadLocationException | IOException e) {
            LOGGER.log(Level.SEVERE, "Error updating HTML style", e);
        }
    }

    private void sendMessage(ActionEvent e) {
        String message = inputField.getText();
        if (StringUtils.isNotBlank(message)) {
            appendMessage("User", message);
            inputField.setText("");
            disableButtons();

            CompletableFuture.supplyAsync(() -> getAIResponse(message))
                    .thenAccept(this::displayAIResponse)
                    .exceptionally(ex -> {
                        LOGGER.log(Level.SEVERE, "Error getting AI response", ex);
                        SwingUtilities.invokeLater(() -> {
                            appendMessage("System", "Error: " + ex.getMessage());
                            enableButtons();
                        });
                        return null;
                    });
        }
    }

    private void sendContinueMessage(ActionEvent e) {
        // TODO: 实现继续功能
        disableButtons();
        // 在这里添加继续逻辑，完成后调用enableButtons()
    }

    private void clearConversation(ActionEvent e) {
        chatHistory.setText("");
        updateHtmlStyle();
    }

    private void startAutoRepeat(ActionEvent e) {
        // TODO: 实现自动重复功能
        disableButtons();
        // 在这里添加自动重复逻辑，完成后调用enableButtons()
    }

    private void appendMessage(String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                HTMLDocument doc = (HTMLDocument) chatHistory.getDocument();
                String htmlMessage = convertToHtml(sender, message);

                // 获取 body 元素
                Element body = doc.getElement(doc.getDefaultRootElement(), StyleConstants.NameAttribute, HTML.Tag.BODY);
                if (body == null) {
                    // 如果找不到 body 元素，就创建一个
                    doc.setOuterHTML(doc.getDefaultRootElement(), "<html><body></body></html>");
                    body = doc.getElement(doc.getDefaultRootElement(), StyleConstants.NameAttribute, HTML.Tag.BODY);
                }

                // 在 body 元素的末尾插入新消息
                doc.insertBeforeEnd(body, htmlMessage);

                // 滚动到最底部
                chatHistory.setCaretPosition(doc.getLength());

                // 打印日志，确认消息已添加
                LOGGER.info("Message added: " + htmlMessage);

            } catch (BadLocationException | IOException ex) {
                LOGGER.log(Level.SEVERE, "Error appending message", ex);
            }
        });
    }
    private String convertToHtml(String sender, String message) {
        Node document = markdownParser.parse(message);
        String htmlContent = htmlRenderer.render(document);

        String messageClass = "user".equalsIgnoreCase(sender) ? "user-message" : "ai-message";
        return String.format(
                "<div class='%s' style='margin-bottom: 10px; padding: 10px; border-radius: 5px;'>" +
                        "<strong>%s:</strong><br>" +
                        "%s" +
                        "</div>",
                messageClass, sender, applyCodeHighlight(htmlContent)
        );
    }

    private String applyCodeHighlight(String html) {
        return html.replaceAll("<pre><code>(.*?)</code></pre>",
                "<pre><code class=\"hljs\">$1</code></pre>");
    }

    private void disableButtons() {
        sendButton.setEnabled(false);
        continueButton.setEnabled(false);
        autoButton.setEnabled(false);
    }

    private void enableButtons() {
        sendButton.setEnabled(true);
        continueButton.setEnabled(true);
        autoButton.setEnabled(true);
    }

    private String getAIResponse(String message) {
        // TODO: 实现实际的AI服务调用
        // 这里应该是一个阻塞调用，返回AI的响应
        try {
            Thread.sleep(2000); // 模拟网络延迟
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "这是AI对 '" + message + "' 的回复。这个回复包含了一些Markdown格式：\n\n" +
                "1. **粗体文本**\n" +
                "2. *斜体文本*\n" +
                "3. `行内代码`\n\n" +
                "代码块示例：\n" +
                "```java\n" +
                "public class HelloWorld {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"Hello, World!\");\n" +
                "    }\n" +
                "}\n" +
                "```\n";
    }

    private void displayAIResponse(String response) {
        String[] words = response.split("\\s+");
        final int[] index = {0};

        executorService.scheduleAtFixedRate(() -> {
            if (index[0] < words.length) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage("AI", words[index[0]] + " ");
                    index[0]++;
                });
            } else {
                executorService.shutdown();
                SwingUtilities.invokeLater(this::enableButtons);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }
}