package com.dobest1.boyka;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ClaudeClient {
    private final String BASE_SYSTEM_PROMPT;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final String apiKey;
    private final String apiUrl;
    private final List<Message> conversationHistory;
    private final ClaudeConfig config;
    private final ToolExecutor toolExecutor;
    private static final int MAX_RETRIES = 3;
    private static final int MAX_RECURSION_DEPTH = 20;
    public void clearConversationHistory() {
        this.conversationHistory.clear();
    }
    public ClaudeClient(ClaudeConfig config, String prompt, ToolExecutor toolExecutor) {
        this.config = config;
        this.apiKey = config.getApiKey();
        this.apiUrl = config.getApiUrl();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getConnectionTimeout(), TimeUnit.SECONDS)
                .readTimeout(config.getReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(config.getWriteTimeout(), TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.conversationHistory = new ArrayList<>();
        this.toolExecutor = toolExecutor;
        this.BASE_SYSTEM_PROMPT = prompt;

    }

    public ClaudeClient(ClaudeConfig config, String prompt) {
        this(config, prompt, null);
    }

    public String sendMessage(String userMessage,List<Tool> availableTools) throws IOException {
        JsonObject requestBody = buildRequestBody(userMessage, availableTools);
        AIClaudeResponse claudeResponse = sendRequest(requestBody);
        return processClaudeResponse(claudeResponse, availableTools, 0);
    }

    public String sendMessageNoHistory(String systemPrompt, String userMessage, String context, List<Tool> availableTools) throws IOException {
        JsonObject requestBody = buildRequestBody(systemPrompt, List.of(new Message[]{new Message("user", userMessage)}),  context, availableTools);
        AIClaudeResponse claudeResponse = sendRequest(requestBody);
        // If max tokens reached, try again with a shorter message
        if (Objects.equals(claudeResponse.stop_reason, "max_tokens")) {
            String finalmessage=claudeResponse.content.get(0).text;
            BoykaAILogger.info("Max tokens reached. Please try again with a shorter message.");
            List<Message> messages = new ArrayList<>();
            messages.add(new Message("user", userMessage));
            messages.add(new Message("assistant", claudeResponse.content));
            messages.add(new Message("user", "Max tokens reached. Please continue"));
            requestBody=buildRequestBody(systemPrompt, messages, context, Collections.emptyList());
            finalmessage+=sendRequest(requestBody).content.get(0).text.replace("<REPLACE>\\n", "");
            return finalmessage;
        }
        return processClaudeResponse(claudeResponse, availableTools, 0);
    }

    private JsonObject buildRequestBody(String userMessage, List<Tool> availableTools) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("max_tokens", config.getMaxTokens());

        BoykaAISettings.State Settings = BoykaAISettings.getInstance().getState();

        assert Settings != null;
        String latestContext = Settings.projectContexts;
        String systemPrompt = BASE_SYSTEM_PROMPT;
        systemPrompt = systemPrompt.replace("<content></content>", "\n\nFile Context: " + latestContext + "\n\n");

        requestBody.addProperty("system", systemPrompt);

        JsonArray messages = new JsonArray();
        for (Message message : conversationHistory) {
            messages.add(message.toJsonObject());
        }
        if (userMessage != null && !userMessage.isEmpty()) {
            messages.add(new Message("user", userMessage).toJsonObject());
            conversationHistory.add(new Message("user", userMessage));
        }
        requestBody.add("messages", messages);

        if (availableTools != null && !availableTools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (Tool tool : availableTools) {
                toolsArray.add(tool.toClaudeFormat());
            }
            requestBody.add("tools", toolsArray);
        }

        return requestBody;
    }

    private JsonObject buildRequestBody(String systemPrompt, List<Message> userMessage, String context, List<Tool> availableTools) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("max_tokens", config.getMaxTokens());
        requestBody.addProperty("system", systemPrompt);

        JsonArray messages = new JsonArray();
        if (userMessage != null && !userMessage.isEmpty()) {
            for (Message message : userMessage) {
                messages.add(message.toJsonObject());
            }
        }
        requestBody.add("messages", messages);

        if (availableTools != null && !availableTools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (Tool tool : availableTools) {
                toolsArray.add(tool.toClaudeFormat());
            }
            requestBody.add("tools", toolsArray);
        }

        return requestBody;
    }

    private AIClaudeResponse sendRequest(JsonObject requestBody) throws IOException {
        Request request = new Request.Builder()
                .url(apiUrl + "messages")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", config.getAnthropicVersion())
                .build();

        BoykaAILogger.info("Claude Request: " + requestBody);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body().string();
                    BoykaAILogger.warn("Attempt " + (attempt + 1) + " - Unexpected code " + response.code() + " " + errorBody);
                    if (attempt == MAX_RETRIES - 1) {
                        throw new IOException("Unexpected code " + response.code() + " " + errorBody);
                    }
                } else {
                    String responseBody = response.body().string();
                    BoykaAILogger.info("Claude Response: " + responseBody);
                    return gson.fromJson(responseBody, AIClaudeResponse.class);
                }
            } catch (IOException e) {
                if (attempt == MAX_RETRIES - 1) {
                    throw e;
                }
                BoykaAILogger.warn("Attempt " + (attempt + 1) + " failed. Retrying...");
            }
            // Exponential backoff
            try {
                Thread.sleep((long) Math.pow(2, attempt) * 1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", ie);
            }
        }
        throw new IOException("Failed after " + MAX_RETRIES + " attempts");
    }

    private String processClaudeResponse(AIClaudeResponse claudeResponse, List<Tool> availableTools, int depth) throws IOException {
        if (depth >= MAX_RECURSION_DEPTH) {
            BoykaAILogger.warn("Max recursion depth reached. Stopping further processing.");
            return "Max recursion depth reached. Stopping further processing.";
        }

        StringBuilder finalResponse = new StringBuilder();
        List<ToolResult> toolResults = new ArrayList<>();

        for (ContentBlock block : claudeResponse.content) {
            if ("text".equals(block.type)) {
                finalResponse.append(block.text).append("\n");
            } else if ("tool_use".equals(block.type)) {
                String toolResult = executeToolCall(block);
                toolResults.add(new ToolResult(block.id, toolResult, false));
            }
        }

        conversationHistory.add(new Message("assistant", claudeResponse.content));

        if (!toolResults.isEmpty()) {
            conversationHistory.add(new Message("user", toolResults));
            String claudeResponseToTool = sendToolResultToClaude(availableTools, depth + 1);
            finalResponse.append(claudeResponseToTool).append("\n");
        }

        return finalResponse.toString().trim();
    }

    private String executeToolCall(ContentBlock toolUseBlock) {
        if (toolExecutor == null) {
            return "Error: Tool execution is not available.";
        }
        try {
            return toolExecutor.executeToolCall(toolUseBlock.name, gson.toJson(toolUseBlock.input));
        } catch (Exception e) {
            BoykaAILogger.error("Error executing tool call", e);
            return "Error: An error occurred during tool execution: " + e.getMessage();
        }
    }

    private String sendToolResultToClaude(List<Tool> availableTools, int depth) throws IOException {
        JsonObject requestBody = buildRequestBody("", availableTools);
        AIClaudeResponse claudeResponse = sendRequest(requestBody);
        return processClaudeResponse(claudeResponse, availableTools, depth);
    }

    private static class AIClaudeResponse {
        String id;
        String model;
        String role;
        List<ContentBlock> content;
        String stop_reason;
    }

    private static class ContentBlock {
        String type;
        String text;
        String id;
        String name;
        JsonObject input;

        JsonObject toJsonObject() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("type", type);
            if (text != null) {
                jsonObject.addProperty("text", text);
            }
            if (id != null) {
                jsonObject.addProperty("id", id);
            }
            if (name != null) {
                jsonObject.addProperty("name", name);
            }
            if (input != null) {
                jsonObject.add("input", input);
            }
            return jsonObject;
        }
    }

    private static class Message {
        String role;
        Object content;

        Message(String role, Object content) {
            this.role = role;
            this.content = content;
        }

        JsonObject toJsonObject() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("role", role);
            if (content instanceof String) {
                jsonObject.addProperty("content", (String) content);
            } else if (content instanceof List) {
                JsonArray contentArray = new JsonArray();
                for (Object item : (List<?>) content) {
                    if (item instanceof ContentBlock) {
                        contentArray.add(((ContentBlock) item).toJsonObject());
                    } else if (item instanceof ToolResult) {
                        contentArray.add(((ToolResult) item).toJsonObject());
                    } else if (item instanceof String) {
                        contentArray.add((String) item);
                    } else if (item instanceof JsonObject) {
                        contentArray.add((JsonObject) item);
                    }
                }
                jsonObject.add("content", contentArray);
            }
            return jsonObject;
        }
    }

    private static class ToolResult {
        String type = "tool_result";
        String tool_use_id;
        String content;
        boolean is_error;

        ToolResult(String tool_use_id, String content, boolean is_error) {
            this.tool_use_id = tool_use_id;
            this.content = content;
            this.is_error = is_error;
        }

        JsonObject toJsonObject() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("type", type);
            jsonObject.addProperty("tool_use_id", tool_use_id);
            jsonObject.addProperty("content", content);
            jsonObject.addProperty("is_error", is_error);
            return jsonObject;
        }
    }
}