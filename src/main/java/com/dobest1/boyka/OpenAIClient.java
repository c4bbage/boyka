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
    private static Gson gson;
    private final OkHttpClient httpClient;
    private final String apiKey;
    private final String apiUrl;
    private final List<Message> conversationHistory;
    private final OpenAIConfig config;
    private final String BASE_SYSTEM_PROMPT;

    private final ToolExecutor toolExecutor;

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
        gson = new Gson();
        this.conversationHistory = new ArrayList<>();
        this.BASE_SYSTEM_PROMPT = prompt;
    }

    public OpenAIClient(OpenAIConfig config, String systemPrompt) {
        this(config, systemPrompt, null);

    }

    public String sendMessage(String userMessage, String context, List<Tool> availableTools) throws IOException {
        JsonObject requestBody = buildRequestBody(userMessage, context, availableTools);
        Request request = new Request.Builder()
                .url(apiUrl + "chat/completions")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();
        BoykaAILogger.info("sendMessage Request: " + requestBody);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                BoykaAILogger.warn("sendMessage Unexpected code " + response.code() + " " + response.body().string());
                throw new IOException("Unexpected code " + response.code() + " " + response.body().string());
            }

            String responseBody = response.body().string();
            BoykaAILogger.info("sendMessage Response: " + responseBody);
            AIOpenAIResponse openAIResponse = gson.fromJson(responseBody, AIOpenAIResponse.class);
            return processOpenAIResponse(openAIResponse, availableTools);
        } catch (Exception e) {
            BoykaAILogger.error("Error in sendMessage", e);
            return "Error: An error occurred during sendMessage: " + e.getMessage();
        }
    }

    public String sendMessageNoHistory(String systemPrompt, String userMessage, String context, List<Tool> availableTools) throws IOException {
        JsonObject requestBody = buildRequestBody(systemPrompt, userMessage, context, availableTools);
        Request request = new Request.Builder()
                .url(apiUrl + "chat/completions")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();
        BoykaAILogger.info("sendMessageNoHistory Request: " + requestBody);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                BoykaAILogger.warn("sendMessageNoHistory Unexpected code " + response.code() + " " + response.body().string());

                throw new IOException("Unexpected code " + response.code() + " " + response.body().string());
            }
            String responseBody = response.body().string();
            AIOpenAIResponse openAIResponse = gson.fromJson(responseBody, AIOpenAIResponse.class);
            BoykaAILogger.info("sendMessageNoHistory Response: " + responseBody);

            return openAIResponse.getFirstChoice().message.content;
        } catch (Exception e) {
            BoykaAILogger.error("Error in sendMessageNoHistory", e);
            return "Error: An error occurred during sendMessageNoHistory: " + e.getMessage();
        }
    }

    private JsonObject buildRequestBody(String userMessage, String context, List<Tool> availableTools) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("max_tokens", config.getMaxTokens());

        JsonArray messages = new JsonArray();
        Message systemMessage = new Message("system", BASE_SYSTEM_PROMPT);

        if (!context.isEmpty()) {
            systemMessage = new Message("system", BASE_SYSTEM_PROMPT.replace("<content></content>", "\n\nFile Context: " + context + "\n\n"));
        }
        messages.add(systemMessage.toJsonObject());
        for (Message message : conversationHistory) {
            messages.add(message.toJsonObject());
        }
        if (userMessage != null && !userMessage.isEmpty()) {
        messages.add(new Message("user", userMessage).toJsonObject());}
        requestBody.add("messages", messages);

        JsonArray functionsArray = new JsonArray();
        for (Tool tool : availableTools) {
            functionsArray.add(tool.toOpenAIFormat());
        }
        requestBody.add("tools", functionsArray);

        return requestBody;
    }

    private JsonObject buildRequestBody(String systemPrompt, String userMessage, String context, List<Tool> availableTools) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", config.getModel());
        requestBody.addProperty("max_tokens", config.getMaxTokens());

        JsonArray messages = new JsonArray();

        Message systemMessage = new Message("system", systemPrompt);


        messages.add(systemMessage.toJsonObject());
        if (userMessage != null && !userMessage.isEmpty()) {

            messages.add(new Message("user", userMessage).toJsonObject());
        }
        requestBody.add("messages", messages);

        if (availableTools != null && !availableTools.isEmpty()) {
            JsonArray functionsArray = new JsonArray();

            for (Tool tool : availableTools) {
                functionsArray.add(tool.toOpenAIFormat());
            }
            requestBody.add("tools", functionsArray);
        }

        return requestBody;
    }

    private String processOpenAIResponse(AIOpenAIResponse openAIResponse, List<Tool> availableTools) throws IOException {
        StringBuilder finalResponse = new StringBuilder();

        if (openAIResponse.choices != null && !openAIResponse.choices.isEmpty()) {
            BoykaAILogger.info("OpenAi response choices size: " + openAIResponse.choices.size());

            for (Choice choice : openAIResponse.choices) {
                if (choice.message != null && choice.message.tool_calls != null) {
                    if (choice.message.content != null && !choice.message.content.isEmpty()) {
                        finalResponse.append(choice.message.content).append("\n");
                    }
                    for (ToolCall toolCall : choice.message.tool_calls) {
                        List<ToolCall> toolCallList = new ArrayList<>();
                        toolCallList.add(toolCall);
                        if (toolCall.function == null) {
                            continue;
                        }

////                    finalResponse.append("Tool used: ").append(toolCall.function.name).append("\n");
//                      finalResponse.append("Result: ").append(executeToolCall(toolCall)).append("\n");
                        String toolResult = executeToolCall(toolCall);
                        conversationHistory.add(new Message("assistant", choice.message.content, toolCallList));
                        conversationHistory.add(new Message("tool", toolResult));
                        String openAIResponseToTool = sendToolResultToOpenAI(toolCall.function.name, toolResult, availableTools);
                        finalResponse.append(openAIResponseToTool).append("\n");
                    }

                }
                if (choice.message != null && choice.message.content != null) {
                    finalResponse.append(choice.message.content).append("\n");
                }

            }
//            Message assistantMessage = openAIResponse.choices.get(0).message;
//            if (assistantMessage.content != null && !assistantMessage.content.isEmpty() &&assistantMessage.tool_calls == null) {
//                finalResponse.append(assistantMessage.content).append("\n");
//                conversationHistory.add(new Message("assistant", assistantMessage.content, assistantMessage.tool_calls));
//                return finalResponse.toString();
//            }
//
//            if (assistantMessage.tool_calls != null) {
//                BoykaAILogger.info("Tool call detected size: "+assistantMessage.tool_calls.size());
//                conversationHistory.add(new Message("assistant", assistantMessage.content, assistantMessage.tool_calls));
//                String toolResult = executeToolCall(assistantMessage.tool_calls.get(0));
////                finalResponse.append("Tool used: ").append(assistantMessage.tool_calls.get(0).function.name)
////                        .append("\nResult: ").append(toolResult).append("\n");
//
//                conversationHistory.add(new Message("tool", toolResult));
//                String openAIResponseToTool = sendToolResultToOpenAI(assistantMessage.tool_calls.get(0).function.name, toolResult, availableTools);
//                finalResponse.append(openAIResponseToTool).append("\n");
//            }
        }

        return finalResponse.toString();
    }

    private String executeToolCall(ToolCall functionCall) {
        if (toolExecutor == null) {
            return "Error: Tool execution is not available.";
        }
        return toolExecutor.executeToolCall(functionCall.function.name, functionCall.function.arguments);
    }

    private String sendToolResultToOpenAI(String functionName, String toolResult, List<Tool> availableTools) throws IOException {
//        JsonObject requestBody = new JsonObject();
//        requestBody.addProperty("model", config.getModel());
//        requestBody.addProperty("max_tokens", config.getMaxTokens());
//        JsonArray messages = new JsonArray();
//        messages.add(new Message("system", BASE_SYSTEM_PROMPT + "\n\nContext: " + toolResult).toJsonObject());
//        for (Message message : conversationHistory) {
//            messages.add(message.toJsonObject());
//        }
//        requestBody.add("messages", messages);
//
//        JsonArray functionsArray = new JsonArray();
//        for (Tool tool : availableTools) {
//            functionsArray.add(tool.toOpenAIFormat());
//        }
//        requestBody.add("tools", functionsArray);
        JsonObject requestBody = buildRequestBody("", "", availableTools);
        Request request = new Request.Builder()
                .url(apiUrl + "chat/completions")
                .post(RequestBody.create(MediaType.parse("application/json"), requestBody.toString()))
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();
        BoykaAILogger.info("sendToolResultToOpenAI Request: " + requestBody);
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                BoykaAILogger.info("sendToolResultToOpenAI no successful Response: " + response.body().string());
                throw new IOException("Unexpected code " + response);
            }
            String responseBody = response.body().string();
            BoykaAILogger.info("sendToolResultToOpenAI Response: " + responseBody);

            AIOpenAIResponse openAIResponse = gson.fromJson(responseBody, AIOpenAIResponse.class);

            if (openAIResponse.choices != null && !openAIResponse.choices.isEmpty()) {
                String responseText = openAIResponse.choices.get(0).message.content;
                conversationHistory.add(new Message("assistant", responseText));
                return responseText;
            }
        } catch (Exception e) {
            BoykaAILogger.error("Error in sendToolResultToOpenAI", e);
            return "Error: An error occurred duringsendToolResultToOpenAI: " + e.getMessage();
        }

        return "No response from OpenAI";
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
        Object logprobs;  // 可以是 null 或者其他类型，取决于实际使用情况
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

        Message(String role, String content, List<ToolCall> tool_calls) {
            this.role = role;
            this.content = content;
            this.tool_calls = tool_calls;
        }

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        JsonObject toJsonObject() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("role", role);
            // 处理 content 为 null 的情况
            jsonObject.addProperty("content", content == null ? null : content);

            if (tool_calls != null) {
                JsonArray toolCallsArray = new JsonArray();
                for (ToolCall toolCall : tool_calls) {
                    JsonObject toolCallObject = new JsonObject();
                    toolCallObject.add("function", toolCall.function.toJsonObject());
                    toolCallsArray.add(toolCallObject);
                }
                jsonObject.add("tool_calls", toolCallsArray);
            }
            return jsonObject;
        }
    }

    private static class ToolCall {
        String id;
        String type;
        Function function;
        int index;
    }

    private static class Function {
        String name;
        String arguments;

        JsonObject toJsonObject() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("name", name);
            jsonObject.addProperty("arguments", arguments);
            //            JsonObject args = gson.fromJson(arguments, JsonObject.class);
//
//            jsonObject.add("arguments", args);
            return jsonObject;
        }
    }
}