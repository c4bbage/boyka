package com.dobest1.boyka;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class OpenAIClient {
    private static final Gson gson = new Gson();
    private final OkHttpClient httpClient;
    private final String apiKey;
    private final String apiUrl;
    private final List<Message> conversationHistory;
    private final OpenAIConfig config;
    private final String BASE_SYSTEM_PROMPT;
    private final ToolExecutor toolExecutor;
    private static final int MAX_RETRIES = 3;
    private static final int MAX_RECURSION_DEPTH = 20;
    public void clearConversationHistory() {
        this.conversationHistory.clear();
    }
    public OpenAIClient(OpenAIConfig config, String prompt, ToolExecutor toolExecutor) {
        this.config = config;
        this.apiKey = config.getApiKey();
        this.apiUrl = config.getApiUrl();
        this.toolExecutor = toolExecutor;

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getConnectionTimeout(), TimeUnit.SECONDS)
                .readTimeout(config.getReadTimeout(), TimeUnit.SECONDS)
                .writeTimeout(config.getWriteTimeout(), TimeUnit.SECONDS)
                .build();
        this.conversationHistory = new ArrayList<>();
        this.BASE_SYSTEM_PROMPT = prompt;

    }

    public OpenAIClient(OpenAIConfig config, String systemPrompt) {
        this(config, systemPrompt, null);
    }

    public String sendMessage(String userMessage,  List<Tool> availableTools) throws IOException {
        JsonObject requestBody = buildRequestBody(userMessage, availableTools);
        AIOpenAIResponse openAIResponse = sendRequest(requestBody);
        return processOpenAIResponse(openAIResponse, availableTools, 0);
    }

    public String sendMessageNoHistory(String systemPrompt, String userMessage, String context, List<Tool> availableTools) throws IOException {
        JsonObject requestBody = buildRequestBody(systemPrompt, userMessage, context, availableTools);
        AIOpenAIResponse openAIResponse = sendRequest(requestBody);
        return processOpenAIResponse(openAIResponse, availableTools, 0);
    }

    private JsonObject buildRequestBody(String userMessage, List<Tool> availableTools) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("max_tokens", config.getMaxTokens());

        JsonArray messages = new JsonArray();
        Message systemMessage = new Message("system", BASE_SYSTEM_PROMPT);
        BoykaAISettings.State Settings = BoykaAISettings.getInstance().getState();

        assert Settings != null;
        String latestContext = Settings.projectContexts;
        systemMessage = new Message("system", BASE_SYSTEM_PROMPT.replace("<content></content>", "\n\nFile Context: " + latestContext + "\n\n"));

        messages.add(systemMessage.toJsonObject());
        for (Message message : conversationHistory) {
            messages.add(message.toJsonObject());
        }
        if (userMessage != null && !userMessage.isEmpty()) {
            messages.add(new Message("user", userMessage).toJsonObject());
        }
        requestBody.add("messages", messages);

        if (availableTools != null && !availableTools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (Tool tool : availableTools) {
                toolsArray.add(tool.toOpenAIFormat());
            }
            requestBody.add("tools", toolsArray);
            requestBody.addProperty("tool_choice", "auto");
        }

        return requestBody;
    }

    private JsonObject buildRequestBody(String systemPrompt, String userMessage, String context, List<Tool> availableTools) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("max_tokens", config.getMaxTokens());

        JsonArray messages = new JsonArray();
        messages.add(new Message("system", systemPrompt).toJsonObject());
        if (userMessage != null && !userMessage.isEmpty()) {
            messages.add(new Message("user", userMessage).toJsonObject());
        }
        requestBody.add("messages", messages);

        if (availableTools != null && !availableTools.isEmpty()) {
            JsonArray toolsArray = new JsonArray();
            for (Tool tool : availableTools) {
                toolsArray.add(tool.toOpenAIFormat());
            }
            requestBody.add("tools", toolsArray);
            requestBody.addProperty("tool_choice", "auto");
        }

        return requestBody;
    }

    private AIOpenAIResponse sendRequest(JsonObject requestBody) throws IOException {
        Request request = new Request.Builder()
                .url(apiUrl + "chat/completions")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        BoykaAILogger.info("Request: " + requestBody);

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
                    BoykaAILogger.info("Response: " + responseBody);
                    return gson.fromJson(responseBody, AIOpenAIResponse.class);
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

    private String processOpenAIResponse(AIOpenAIResponse openAIResponse, List<Tool> availableTools, int depth) throws IOException {
        if (depth >= MAX_RECURSION_DEPTH) {
            BoykaAILogger.warn("Max recursion depth reached. Stopping further processing.");
            return "Max recursion depth reached. Stopping further processing.";
        }

        StringBuilder finalResponse = new StringBuilder();

        if (openAIResponse.choices != null && !openAIResponse.choices.isEmpty()) {
            for (Choice choice : openAIResponse.choices) {
                if (choice.message != null) {
                    if (choice.message.content != null && !choice.message.content.isEmpty()) {
                        finalResponse.append(choice.message.content).append("\n");
                    }
                    conversationHistory.add(choice.message);

                    if ("tool_calls".equals(choice.finish_reason) && choice.message.tool_calls != null && !choice.message.tool_calls.isEmpty()) {
                        for (ToolCall toolCall : choice.message.tool_calls) {
                            String toolResult = executeToolCall(toolCall);
                            conversationHistory.add(new Message("tool", toolResult, toolCall.id));
                        }
                        String openAIResponseToTool = sendToolResultToOpenAI(availableTools, depth + 1);
                        finalResponse.append(openAIResponseToTool).append("\n");
                    } else if ("stop".equals(choice.finish_reason)) {
                        // Normal completion, no further action needed
                    } else if ("length".equals(choice.finish_reason)) {
                        BoykaAILogger.warn("Response truncated due to max_tokens limit.");
                        finalResponse.append("(Response truncated due to length limit)");
                    } else if ("content_filter".equals(choice.finish_reason)) {
                        BoykaAILogger.warn("Response filtered due to content policy.");
                        finalResponse.append("(Response filtered due to content policy)");
                    } else {
                        BoykaAILogger.warn("Unexpected finish_reason: " + choice.finish_reason);
                    }
                }
            }
        }

        return finalResponse.toString().trim();
    }

    private String executeToolCall(ToolCall toolCall) {
        if (toolExecutor == null) {
            return "Error: Tool execution is not available.";
        }
        try {
            return toolExecutor.executeToolCall(toolCall.function.name, toolCall.function.arguments);
        } catch (Exception e) {
            BoykaAILogger.error("Error executing tool call", e);
            return "Error: An error occurred during tool execution: " + e.getMessage();
        }
    }

    private String sendToolResultToOpenAI(List<Tool> availableTools, int depth) throws IOException {
        JsonObject requestBody = buildRequestBody("", availableTools);
        AIOpenAIResponse openAIResponse = sendRequest(requestBody);
        return processOpenAIResponse(openAIResponse, availableTools, depth);
    }

    private static class AIOpenAIResponse {
        String id;
        String object;
        long created;
        String model;
        String system_fingerprint;
        List<Choice> choices;
        Usage usage;

        public Choice getFirstChoice() {
            return (choices != null && !choices.isEmpty()) ? choices.get(0) : null;
        }
    }

    private static class Choice {
        int index;
        Message message;
        String finish_reason;
        Object logprobs;
    }

    private static class Usage {
        int prompt_tokens;
        int completion_tokens;
        int total_tokens;
    }

    private static class Message {
        String role;
        String content;
        List<ToolCall> tool_calls;
        String tool_call_id;

        Message(String role, String content) {
            this(role, content, null, null);
        }

        Message(String role, String content, String tool_call_id) {
            this(role, content, null, tool_call_id);
        }

        Message(String role, String content, List<ToolCall> tool_calls, String tool_call_id) {
            this.role = role;
            this.content = content;
            this.tool_calls = tool_calls;
            this.tool_call_id = tool_call_id;
        }

        JsonObject toJsonObject() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("role", role);
            if (content != null) {
                jsonObject.addProperty("content", content);
            }
            if (tool_calls != null && !tool_calls.isEmpty()) {
                JsonArray toolCallsArray = new JsonArray();
                for (ToolCall toolCall : tool_calls) {
                    toolCallsArray.add(toolCall.toJsonObject());
                }
                jsonObject.add("tool_calls", toolCallsArray);
            }
            if (tool_call_id != null) {
                jsonObject.addProperty("tool_call_id", tool_call_id);
            }
            return jsonObject;
        }
    }

    private static class ToolCall {
        String id;
        String type;
        Function function;

        JsonObject toJsonObject() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("id", id);
            jsonObject.addProperty("type", type);
            jsonObject.add("function", function.toJsonObject());
            return jsonObject;
        }
    }

    private static class Function {
        String name;
        String arguments;

        JsonObject toJsonObject() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("name", name);
            jsonObject.addProperty("arguments", arguments);
            return jsonObject;
        }
    }
}