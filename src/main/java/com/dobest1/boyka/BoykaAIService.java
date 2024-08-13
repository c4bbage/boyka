package com.dobest1.boyka;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonSyntaxException;
import okhttp3.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class BoykaAIService {
    private static final int TIMEOUT_MINUTES = 10;
    private static final String BASE_SYSTEM_PROMPT = """
    You are Claude, an AI assistant powered by Anthropic's Claude-3.5-Sonnet model, specialized in software development with access to a variety of tools and the ability to instruct and direct a coding agent and a code execution one. Your capabilities include:

    1. Creating and managing project structures
    2. Writing, debugging, and improving code across multiple languages
    3. Providing architectural insights and applying design patterns
    4. Staying current with the latest technologies and best practices
    5. Analyzing and manipulating files within the project directory
    6. Performing web searches for up-to-date information
    7. Executing code and analyzing its output within an isolated 'code_execution_env' virtual environment
    8. Managing and stopping running processes started within the 'code_execution_env'

    Available tools and their optimal use cases:
    [List of tools with descriptions]

    Tool Usage Guidelines:
    - Always use the most appropriate tool for the task at hand.
    - Provide detailed and clear instructions when using tools, especially for edit_and_apply.
    - After making changes, always review the output to ensure accuracy and alignment with intentions.
    - Use execute_code to run and test code within the 'code_execution_env' virtual environment, then analyze the results.
    - For long-running processes, use the process ID returned by execute_code to stop them later if needed.
    - Proactively use tavily_search when you need up-to-date information or additional context.
    - When working with multiple files, consider using read_multiple_files for efficiency.

    Error Handling and Recovery:
    - If a tool operation fails, carefully analyze the error message and attempt to resolve the issue.
    - For file-related errors, double-check file paths and permissions before retrying.
    - If a search fails, try rephrasing the query or breaking it into smaller, more specific searches.
    - If code execution fails, analyze the error output and suggest potential fixes, considering the isolated nature of the environment.
    - If a process fails to stop, consider potential reasons and suggest alternative approaches.

    Project Creation and Management:
    1. Start by creating a root folder for new projects.
    2. Create necessary subdirectories and files within the root folder.
    3. Organize the project structure logically, following best practices for the specific project type.

    Always strive for accuracy, clarity, and efficiency in your responses and actions. Your instructions must be precise and comprehensive. If uncertain, use the tavily_search tool or admit your limitations. When executing code, always remember that it runs in the isolated 'code_execution_env' virtual environment. Be aware of any long-running processes you start and manage them appropriately, including stopping them when they are no longer needed.
    """;

    private static final String AUTOMODE_SYSTEM_PROMPT = """
    You are currently in automode. Follow these guidelines:

    1. Goal Setting:
       - Set clear, achievable goals based on the user's request.
       - Break down complex tasks into smaller, manageable goals.

    2. Goal Execution:
       - Work through goals systematically, using appropriate tools for each task.
       - Utilize file operations, code writing, and web searches as needed.
       - Always read a file before editing and review changes after editing.

    3. Progress Tracking:
       - Provide regular updates on goal completion and overall progress.
       - Use the iteration information to pace your work effectively.

    4. Tool Usage:
       - Leverage all available tools to accomplish your goals efficiently.
       - Prefer edit_and_apply for file modifications, applying changes in chunks for large edits.
       - Use tavily_search proactively for up-to-date information.

    5. Error Handling:
       - If a tool operation fails, analyze the error and attempt to resolve the issue.
       - For persistent errors, consider alternative approaches to achieve the goal.

    6. Automode Completion:
       - When all goals are completed, respond with "AUTOMODE_COMPLETE" to exit automode.
       - Do not ask for additional tasks or modifications once goals are achieved.

    Remember: Focus on completing the established goals efficiently and effectively. Avoid unnecessary conversations or requests for additional tasks.
    """;

    private final OkHttpClient client;
    private final Gson gson;
    private final BoykaAIFileTools fileTools;
    private final ContextManager contextManager;
    private BoykaAISettings.State settings;

    public BoykaAIService(BoykaAIFileTools fileTools, ContextManager contextManager) {
        this.fileTools = fileTools;
        this.contextManager = contextManager;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .readTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .writeTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .build();
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

        StringBuilder conversationHistory = new StringBuilder();
        conversationHistory.append("User: ").append(message).append("\n\n");
        StringBuilder finalResponse = new StringBuilder();
        Boolean isToolCall = false;
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
            BoykaAILogger.info("AI Response: " + responseBody);
            for (Choice choice : aiResponse.choices) {
                finalResponse.append(choice.message.content).append("\n");
                if (choice.toolCalls != null) {
                    isToolCall = true;
                    for (ToolCall toolCall : choice.toolCalls) {
                        String toolResult = executeToolCall(toolCall);
                        finalResponse.append("工具调用: ").append(toolCall.function.name)
                                .append("\n结果: ").append(toolResult).append("\n");
                        // 添加工具调用和结果到对话历史
                        BoykaAILogger.info("Assistant: Using tool " + toolCall.function.name+" with arguments " + toolCall.arguments+" and result "+toolResult);
                        conversationHistory.append("Assistant: Using tool ").append(toolCall.function.name).append("\n");
                        conversationHistory.append("Tool Result: ").append(toolResult).append("\n\n");
                        // Update context based on tool call results
                        if (toolCall.function.name.equals("read_file") || toolCall.function.name.equals("create_file")) {
                            JsonObject args = gson.fromJson(toolCall.arguments, JsonObject.class);
                            String filePath = args.get("path").getAsString();
                            contextManager.updateFileContent(filePath, toolResult);
                        }
                    }
                }
            }

            // 进行额外的 AI 调用来分析工具调用结果
            String analysisPrompt = "Analyze the following conversation and tool results, then provide a comprehensive response:\n\n" + conversationHistory.toString();
            // Potential additional AI call to analyze tool call results
            String analysisResponse = "";
            try {
                AIRequest analysisRequest = new AIRequest(analysisPrompt, availableTools, model);
                RequestBody analysisBody = RequestBody.create(
                        gson.toJson(analysisRequest),
                        MediaType.parse("application/json")
                );

                Request analysisApiRequest = new Request.Builder()
                        .url(url)
                        .post(analysisBody)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .build();

                Response analysisApiResponse = client.newCall(analysisApiRequest).execute();
                if (analysisApiResponse.isSuccessful()) {
                    String analysisResponseBody = analysisApiResponse.body() != null ? analysisApiResponse.body().string() : "";
                    AIResponse analysisAiResponse = gson.fromJson(analysisResponseBody, AIResponse.class);
                    if (analysisAiResponse.choices != null && analysisAiResponse.choices.length > 0) {
                        analysisResponse = analysisAiResponse.choices[0].message.content;
                    }
                }
            } catch (Exception e) {
                BoykaAILogger.error("Error during analysis API call", e);
                analysisResponse = "Error analyzing tool call results: " + e.getMessage();
            }

            finalResponse.append("\n\nAnalysis of tool call results:\n").append(analysisResponse);

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
        StringBuilder prompt = new StringBuilder(BASE_SYSTEM_PROMPT);
        prompt.append("\n\nCurrent context:\n").append(context);
        prompt.append("\n\nAvailable tools:\n");
        for (Tool tool : availableTools) {
            prompt.append("- ").append(tool.getName()).append(": ").append(tool.getDescription()).append("\n");
        }
        prompt.append("\nUser message: ").append(userMessage);
        return prompt.toString();
    }

    private String executeToolCall(ToolCall toolCall) {
        try {
            JsonObject args = gson.fromJson(toolCall.arguments, JsonObject.class);
            switch (toolCall.function.name) {
                case "create_file":
                    return fileTools.createFile(args.get("path").getAsString(), args.get("content").getAsString());
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
                    return "Unknown tool call: " + toolCall.function.name;
            }
        } catch (Exception e) {
            BoykaAILogger.error("Error in tool execution", e);
            return "An error occurred during tool execution: " + e.getMessage();
        }
    }

    public void runAutoMode(String initialGoal, int maxIterations) {
        for (int i = 0; i < maxIterations; i++) {
            String iterationInfo = "You are currently on iteration " + (i + 1) + " out of " + maxIterations + " in automode.";
            String context = contextManager.getFullContext();
            String systemPrompt = BASE_SYSTEM_PROMPT + "\n\n" + AUTOMODE_SYSTEM_PROMPT.replace("{iteration_info}", iterationInfo);

            List<Tool> availableTools = createAvailableTools();
            String response = getAIResponse(initialGoal, "OpenAI", availableTools, context, settings.selectedModel);

            // Process response, execute tool calls, etc.
            if (response.contains("AUTOMODE_COMPLETE")) {
                break;
            }
            initialGoal = "Continue with the next step.";
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

    // Inner classes for request/response handling
    private static class AIRequest {
        String model;
        Message[] messages;
        List<Tool> tools;

        AIRequest(String prompt, List<Tool> availableTools, String model) {
            this.model = model;
            this.messages = new Message[]{new Message("system", prompt)};
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
        Function function;
        String arguments;
    }

    private static class Function {
        String name;
    }
}