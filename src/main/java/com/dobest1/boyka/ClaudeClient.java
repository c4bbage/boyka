package com.dobest1.boyka;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

    public String sendMessage(String userMessage, String context, List<Tool> availableTools) throws IOException {
        JsonObject requestBody = buildRequestBody(userMessage, context, availableTools);
        //timeout
        Request request = new Request.Builder()
                .url(apiUrl + "messages")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", config.getAnthropicVersion())
                .build();
        BoykaAILogger.info("sendMessage Request: " + requestBody);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                BoykaAILogger.warn("sendMessage Unexpected code " + response.code() + " " + response.body().string());
                throw new IOException("Unexpected code " + response.code() + " " + response.body().string());
            }

            String responseBody = response.body().string();
            AIClaudeResponse claudeResponse = gson.fromJson(responseBody, AIClaudeResponse.class);
            BoykaAILogger.info("sendMessage Response: " + responseBody);
            String finalResponse = processClaudeResponse(claudeResponse, availableTools);
            return finalResponse;
        }
    }

    public String sendMessageNoHistory(String systemPrompt, String userMessage, String context, List<Tool> availableTools) throws IOException {
        JsonObject requestBody = buildRequestBody(systemPrompt, userMessage, context, availableTools);
        Request request = new Request.Builder()
                .url(apiUrl + "messages")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", config.getAnthropicVersion())
                .build();
        BoykaAILogger.info("sendMessageNoHistory Request: " + requestBody);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                BoykaAILogger.warn("sendMessageNoHistory Unexpected code " + response.code() + " " + response.body().string());
                throw new IOException("Unexpected code " + response + " " + response.body().string());
            }

            String responseBody = response.body().string();
            AIClaudeResponse claudeResponse = gson.fromJson(responseBody, AIClaudeResponse.class);
            BoykaAILogger.info("sendMessageNoHistory Response: " + responseBody);
//            String finalResponse = processClaudeResponse(claudeResponse, availableTools);
            return claudeResponse.content.get(0).text;
        }
    }


    private JsonObject buildRequestBody(String userMessage, String context, List<Tool> availableTools) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("max_tokens", config.getMaxTokens());
        requestBody.addProperty("system", BASE_SYSTEM_PROMPT);
        if (!context.isEmpty()) {
            requestBody.addProperty("system", BASE_SYSTEM_PROMPT.replace("<content></content>", "\n\nFile Context: " + context + "\n\n"));
        }
        JsonArray messages = new JsonArray();
        for (Message message : conversationHistory) {
            messages.add(message.toJsonObject());
        }
        if (userMessage != null && !userMessage.isEmpty()) {

            messages.add(new Message("user", userMessage).toJsonObject());
            conversationHistory.add(new Message("user", userMessage));
        }
        requestBody.add("messages", messages);

        JsonArray toolsArray = new JsonArray();
        for (Tool tool : availableTools) {
            toolsArray.add(tool.toClaudeFormat());
        }
        requestBody.add("tools", toolsArray);

        return requestBody;
    }

    private JsonObject buildRequestBody(String systemPrompt, String userMessage, String context, List<Tool> availableTools) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("max_tokens", config.getMaxTokens());
        requestBody.addProperty("system", systemPrompt);

        JsonArray messages = new JsonArray();
        if (userMessage != null && !userMessage.isEmpty()) {

            messages.add(new Message("user", userMessage).toJsonObject());
        }
        requestBody.add("messages", messages);
        if (!availableTools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (Tool tool : availableTools) {
                toolsArray.add(tool.toClaudeFormat());
            }
            requestBody.add("tools", toolsArray);
        }

        return requestBody;
    }


    private String processClaudeResponse(AIClaudeResponse claudeResponse, List<Tool> availableTools) throws IOException {
        StringBuilder finalResponse = new StringBuilder();

        for (ContentBlock block : claudeResponse.content) {
            String  text ="";
            if ("text".equals(block.type)) {
                finalResponse.append(block.text).append("\n");
            } else if ("tool_use".equals(block.type)) {
                String toolResult = executeToolCall(block);
                conversationHistory.add(new Message("assistant", List.of(block)));
//                finalResponse.append("Tool used: ").append(block.name)
//                        .append("\nResult: ").append(toolResult).append("\n");
                conversationHistory.add(new Message("user", List.of(
                        new ToolResult(block.id, toolResult, false)
                )));

                String claudeResponseToTool = sendToolResultToClaude(block.id, toolResult, availableTools);

                finalResponse.append(claudeResponseToTool).append("\n");
            }
        }

        return finalResponse.toString();
    }

    private String executeToolCall(ContentBlock toolUseBlock) {
        if (toolExecutor == null) {
            return "Error: Tool execution is not available.";
        }
        return toolExecutor.executeToolCall(toolUseBlock.name, gson.toJson(toolUseBlock.input));
    }

    private String sendToolResultToClaude(String toolUseId, String toolResult, List<Tool> availableTools) throws IOException {
//        JsonObject requestBody = new JsonObject();
//        requestBody.addProperty("model", config.getModel());
//        requestBody.addProperty("max_tokens", config.getMaxTokens());
//
//        JsonArray messages = new JsonArray();
//        for (Message message : conversationHistory) {
//            messages.add(message.toJsonObject());
//        }
//        JsonArray toolsArray = new JsonArray();
//        for (Tool tool : availableTools) {
//            toolsArray.add(tool.toClaudeFormat());
//        }
//        requestBody.add("tools", toolsArray);
//        requestBody.add("messages", messages);
        JsonObject requestBody = buildRequestBody( "", "", availableTools);
        Request request = new Request.Builder()
                .url(apiUrl + "messages")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", config.getAnthropicVersion())
                .build();
        BoykaAILogger.info("sendToolResultToClaude Request: " + requestBody);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                BoykaAILogger.warn("sendToolResultToClaude Unexpected code " + response.code() + " " + response.body().string());
                throw new IOException("sendToolResultToClaude Unexpected code " + response.code() + " " + response.body().string());
            }

            String responseBody = response.body().string();
            AIClaudeResponse claudeResponse = gson.fromJson(responseBody, AIClaudeResponse.class);

            if (claudeResponse.content != null && !claudeResponse.content.isEmpty()) {
                String responseText = claudeResponse.content.get(0).text;
                conversationHistory.add(new Message("assistant", responseText));
                return responseText;
            }
        } catch (Exception e) {
            BoykaAILogger.warn("sendToolResultToClaude Unexpected code " + e.getMessage());
            BoykaAILogger.error("Error in tool execution", e);
            return "Error: An error occurred during tool execution: " + e.getMessage();
        }

        return "No response from Claude";
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

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        Message(String role, List<Object> content) {
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