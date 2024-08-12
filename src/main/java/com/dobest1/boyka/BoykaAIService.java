package com.dobest1.boyka;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import okhttp3.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

public class BoykaAIService {
    private static final int TIMEOUT_MINUTES = 2;

    private final OkHttpClient client;
    private final Gson gson;
    private final BoykaAIFileTools fileTools;
    private BoykaAISettings.State settings;

    public BoykaAIService(BoykaAIFileTools fileTools) {
        this.fileTools = fileTools;
        try {
            Class.forName("okhttp3.OkHttpClient");
            this.client = new OkHttpClient.Builder()
                    .connectTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
                    .readTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
                    .writeTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
                    .build();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load OkHttpClient", e);
        }
        this.gson = new Gson();
        this.settings = BoykaAISettings.getInstance().getState();
    }

    public void updateSettings(BoykaAISettings.State newSettings) {
        this.settings = newSettings;
    }
    public boolean validateSettings() {
        if (settings.openAIBaseAddress.isEmpty() || settings.openAIKey.isEmpty()) {
            return false;
        }

        String testUrl = settings.openAIBaseAddress + "v1/models";
        Request request = new Request.Builder()
                .url(testUrl)
                .addHeader("Authorization", "Bearer " + settings.openAIKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            BoykaAILogger.error("Error validating settings", e);
            return false;
        }
    }
    public String getAIResponse(String message, String provider, List<Tool> availableTools, String context, String model) {
        String url = provider.equals("OpenAI")
                ? settings.openAIBaseAddress + "v1/chat/completions"
                : settings.claudeAddress;
        String apiKey = provider.equals("OpenAI") ? settings.openAIKey : settings.claudeKey;

        String prompt = createPrompt(message, context, availableTools);
        AIRequest aiRequest = new AIRequest(prompt, availableTools, model);
        RequestBody body = RequestBody.create(
                gson.toJson(aiRequest),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                BoykaAILogger.error("API request failed", new IOException("Unexpected code " + response.code() + ". Error body: " + errorBody));
                return "Error: API request failed with code " + response.code() + ". Please check your settings and try again.";
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            if (responseBody.isEmpty()) {
                BoykaAILogger.error("Empty response body", new IOException("Empty response body"));
                return "Error: Received empty response from the API. Please try again.";
            }

            AIResponse aiResponse = gson.fromJson(responseBody, AIResponse.class);

            StringBuilder finalResponse = new StringBuilder();
            for (Choice choice : aiResponse.choices) {
                finalResponse.append(choice.message.content).append("\n");
                if (choice.toolCalls != null) {
                    for (ToolCall toolCall : choice.toolCalls) {
                        String toolResult = executeToolCall(toolCall);
                        finalResponse.append("工具调用: ").append(toolCall.function.getName())
                                .append("\n结果: ").append(toolResult).append("\n");
                    }
                }
            }

            return finalResponse.toString();
        } catch (IOException e) {
            BoykaAILogger.error("Error during API call", e);
            return "Error: " + e.getMessage() + ". Please check your network connection and try again.";
        } catch (JsonSyntaxException e) {
            BoykaAILogger.error("Error parsing JSON response", e);
            return "Error: Failed to parse the API response. Please try again or contact support.";
        }
    }

    private String createPrompt(String userMessage, String context, List<Tool> availableTools) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an AI assistant for a Java IDE plugin. ");
        prompt.append("You have access to the following context:\n").append(context).append("\n");
        prompt.append("Available tools:\n");
        for (Tool tool : availableTools) {
            prompt.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
        }
        prompt.append("User message: ").append(userMessage).append("\n");
        prompt.append("Please provide a helpful response, using tools when necessary. ");
        prompt.append("If you use a tool, format your response as: [TOOL_NAME](arg1, arg2, ...)");
        return prompt.toString();
    }

    private String executeToolCall(ToolCall toolCall) {
        try {
        JsonObject args = gson.fromJson(toolCall.arguments, JsonObject.class);
        switch (toolCall.function.getName()) {
            case "create_file":
                String path = args.get("path").getAsString();
                String content = args.get("content").getAsString();
                return fileTools.createFile(path, content);
            case "edit_and_apply":
                return fileTools.editAndApply(
                    args.get("path").getAsString(),
                    args.get("instructions").getAsString(),
                    args.get("project_context").getAsString()
                );
            case "execute_code":
                return fileTools.executeCode(args.get("code").getAsString());
            case "stop_process":
                return fileTools.stopProcess(args.get("process_id").getAsString());
            case "read_file":
                return fileTools.readFile(args.get("path").getAsString());
            case "read_multiple_files":
                JsonArray pathsArray = args.getAsJsonArray("paths");
                List<String> paths = new ArrayList<>();
                pathsArray.forEach(element -> paths.add(element.getAsString()));
                return String.join("\n\n", fileTools.readMultipleFiles(paths));
            case "list_files":
                return String.join("\n", fileTools.listFiles(args.get("path").getAsString()));
            default:
                return "未知的工具调用: " + toolCall.function.getName();
        }
        } catch (Exception e) {
            BoykaAILogger.error("Error executing tool call", e);
            return "Error: " + e.getMessage();
        }
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
            "在指定路径创建具有给定内容的新文件。当需要在项目结构中创建新文件时使用此工具。如果不存在，它将创建所有必要的父目录。如果文件创建成功，工具将返回成功消息，如果创建文件时出现问题或文件已存在，则返回错误消息。内容应尽可能完整和有用，包括必要的导入、函数定义和注释。",
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
            "根据特定指令和详细的项目上下文对文件应用AI驱动的改进。此函数读取文件，使用带有对话历史和全面代码��关项目上下文的AI分批处理它。它生成差异并允许用户在应用更改之前确认。目标是保持一致性并防止破坏文件之间的连接。此工具应用于需要理解更广泛项目上下文的复杂代码修改。",
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
            "通过其ID停止运行的进程。此工具用于终止由execute_code工具启动的长时间运行的进程。它将尝试优雅地停止进程，但如果必要，可能会强制终止。如果进程被停止，工具将返回成功消息，如果进程不存在或无法停止，则返回错误消息。",
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
            "读取指定路径的多个文件的内容。当需要一次检查多个现有文件的内容时使用此工具。它将返回读取每个文件的状态，并将成功读取的文件内容存储在系统提示中。如果文件不存在或无法读取，将为该文件返回适当的错误消息。",
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

    private static class AIRequest {
        String model;
        Message[] messages;
        List<Tool> tools;

        AIRequest(String prompt, List<Tool> availableTools, String model) {
            this.model = model;
            this.messages = new Message[]{
                new Message("system", prompt)
            };
            this.tools = availableTools;
        }
    }

    private static class AIResponse {
        Choice[] choices;
    }

    private static class Choice {
        Message message;
        List<ToolCall> toolCalls;
    }

    private static class Message {
        String role;
        String content;

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static class ToolCall {
        Tool function;
        String arguments;
    }
}