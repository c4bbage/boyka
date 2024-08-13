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
    private List<Message> conversationHistory = new ArrayList<>();

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
    private RequestBody createOpenAIRequestBody(String context, List<Tool> availableTools, String model) {
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("model", model);

        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", BASE_SYSTEM_PROMPT + "\n\nContext: " + context);
        messages.add(systemMessage);

        // 添加对话历史
        for (Message message : conversationHistory) {
            JsonObject messageObject = new JsonObject();
            messageObject.addProperty("role", message.role);
            messageObject.addProperty("content", message.content);
            messages.add(messageObject);
        }

        jsonBody.add("messages", messages);

        JsonArray toolsArray = new JsonArray();
        for (Tool tool : availableTools) {
            toolsArray.add(tool.toOpenAIFormat());
        }
        jsonBody.add("tools", toolsArray);


        return RequestBody.create(MediaType.parse("application/json"), jsonBody.toString());
    }

    private RequestBody createClaudeRequestBody(String context, List<Tool> availableTools, String model) {
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("model", settings.claudeModel);
        jsonBody.addProperty("max_tokens", settings.maxTokens);
        jsonBody.addProperty("system",BASE_SYSTEM_PROMPT+ "\n\nContext: " + context);
        JsonArray messages = new JsonArray();

        // 添加对话历史
        for (Message message : conversationHistory) {
            JsonObject messageObject = new JsonObject();
            messageObject.addProperty("role", message.role);
            messageObject.addProperty("content", message.content);
            messages.add(messageObject);
        }

        jsonBody.add("messages", messages);

        JsonArray toolsArray = new JsonArray();
        for (Tool tool : availableTools) {
            toolsArray.add(tool.toClaudeFormat());
        }
        jsonBody.add("tools", toolsArray);
        BoykaAILogger.info("API Request Body: " + jsonBody.toString());
        return RequestBody.create(MediaType.parse("application/json"), jsonBody.toString());
    }
    public String getAIResponse(String userMessage, List<Tool> availableTools, String context, String model) {
        // 添加用户消息到对话历史
        conversationHistory.add(new Message("user", userMessage));

        String url;
        String apiKey;
        RequestBody body;
        Request.Builder requestBuilder = new Request.Builder();

        if (settings.enableOpenai) {
            url = settings.openAIBaseAddress + "v1/chat/completions";
            apiKey = settings.openAIKey;
            body = createOpenAIRequestBody(context, availableTools, model);
            requestBuilder.addHeader("Authorization", "Bearer " + apiKey);
        } else {
            url = settings.claudeAddress + "v1/messages";
            apiKey = settings.claudeKey;
            body = createClaudeRequestBody(context, availableTools, model);
            requestBuilder.addHeader("x-api-key", apiKey);
            requestBuilder.addHeader("anthropic-version", "2023-06-01");
        }

        requestBuilder.url(url)
                .post(body)
                .addHeader("content-type", "application/json");
        BoykaAILogger.info("API Request: " + url);
        BoykaAILogger.info("API Request Body: " + body.toString());

        Request request = requestBuilder.build();

        StringBuilder finalResponse = new StringBuilder();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                BoykaAILogger.error("API request failed", new IOException("Unexpected code " + response.code() + ". Error body: " + errorBody));
                return "Error: API request failed with code " + response.code() + ". Please check your settings and try again.";
            }
            String responseBody = response.body() != null ? response.body().string() : "";
            BoykaAILogger.info("API Response: " + responseBody);

            if (responseBody.isEmpty()) {
                BoykaAILogger.error("Empty response body", new IOException("Empty response body"));
                return "Error: Received empty response from the API. Please try again.";
            }

            if (settings.enableOpenai) {
                AIResponse aiResponse = gson.fromJson(responseBody, AIResponse.class);
                processOpenAIResponse(aiResponse, finalResponse, availableTools, url, apiKey, model);
            } else {
                AIClaudeResponse claudeResponse = gson.fromJson(responseBody, AIClaudeResponse.class);
                processClaudeResponse(claudeResponse, finalResponse, availableTools, url, apiKey, model);
            }

            // 进行额外的 AI 调用来分析工具调用结果
            String analysisPrompt = "Analyze the following conversation and tool results, then provide a comprehensive response:\n\n" + getConversationHistoryString();

            if (settings.enableOpenai) {
                String analysisResult = getOpenAIAnalysis(analysisPrompt, availableTools, url, apiKey, model);
                finalResponse.append("\n\nAnalysis of conversation and tool results:\n").append(analysisResult);
                conversationHistory.add(new Message("analysis", analysisResult));
            } else {
                String analysisResult = getClaudeAnalysis(analysisPrompt, availableTools, url, apiKey, model);
                finalResponse.append("\n\nAnalysis of conversation and tool results:\n").append(analysisResult);
                conversationHistory.add(new Message("user", analysisResult));
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
    private void processOpenAIResponse(AIResponse aiResponse, StringBuilder finalResponse, List<Tool> availableTools, String url, String apiKey, String model) throws IOException {
        if (aiResponse.choices == null || aiResponse.choices.length == 0) {
            BoykaAILogger.warn("Received empty choices from OpenAI API");
            return;
        }

        for (Choice choice : aiResponse.choices) {
            if (choice.message != null) {
                if (choice.message.content != null) {
                    finalResponse.append(choice.message.content).append("\n");
                    conversationHistory.add(new Message("assistant", choice.message.content));
                }
                if (choice.message.tool_calls != null) {
                    for (ToolCall toolCall : choice.message.tool_calls) {
                        if (toolCall.function != null && toolCall.function.name != null) {
                            String toolResult = executeToolCall(toolCall);
                            finalResponse.append("工具调用: ").append(toolCall.function.name)
                                    .append("\n结果: ").append(toolResult).append("\n");
                            conversationHistory.add(new Message("tool", "Tool: " + toolCall.function.name + "\nResult: " + toolResult));
                            updateContextIfNeeded(toolCall, toolResult);

                            // 发送工具调用结果回 OpenAI
                            String followUpResponse = sendToolResultToOpenAI(toolCall, toolResult, availableTools, url, apiKey, model);
                            finalResponse.append("OpenAI 跟进响应: ").append(followUpResponse).append("\n");
                            conversationHistory.add(new Message("user", followUpResponse));
                        } else {
                            BoykaAILogger.warn("Received invalid tool call from OpenAI API");
                        }
                    }
                }
            } else {
                BoykaAILogger.warn("Received choice with null message from OpenAI API");
            }
        }
    }

    private void processClaudeResponse(AIClaudeResponse claudeResponse, StringBuilder finalResponse, List<Tool> availableTools, String url, String apiKey, String model) throws IOException {
        if (claudeResponse.content != null) {
            for (ContentBlock block : claudeResponse.content) {
                if (block == null) {
                    BoykaAILogger.warn("Received null content block from Claude API");
                    continue;
                }

                if ("text".equals(block.type)) {
                    if (block.text != null) {
                        finalResponse.append(block.text).append("\n");
                        conversationHistory.add(new Message("assistant", block.text));
                    } else {
                        BoykaAILogger.warn("Received text block with null content from Claude API");
                    }
                } else if ("tool_use".equals(block.type)) {
                    if (block.name == null || block.input == null) {
                        BoykaAILogger.warn("Received invalid tool_use data from Claude API");
                        continue;
                    }

                    finalResponse.append("工具调用: ").append(block.name)
                            .append("\n输入: ").append(gson.toJson(block.input)).append("\n");

                    ToolCall toolCall = new ToolCall();
                    toolCall.id = block.id;
                    toolCall.function = new Function();
                    toolCall.function.name = block.name;
                    toolCall.function.arguments = gson.toJson(block.input);

                    String toolResult = executeToolCall(toolCall);
                    finalResponse.append("结果: ").append(toolResult).append("\n");

                    conversationHistory.add(new Message("tool", "Tool: " + block.name +
                            "\nInput: " + gson.toJson(block.input) +
                            "\nResult: " + toolResult));

                    updateContextIfNeeded(toolCall, toolResult);

                    // 发送工具调用结果回 Claude
                    String claudeResponseText = sendToolResultToClaude(block.id, toolResult, url, apiKey, model);
                    finalResponse.append("Claude 响应: ").append(claudeResponseText).append("\n");
                    conversationHistory.add(new Message("assistant", claudeResponseText));
                } else {
                    BoykaAILogger.warn("Received unknown content type from Claude API: " + block.type);
                }
            }
        } else {
            BoykaAILogger.warn("Received null content from Claude API");
        }

        if (claudeResponse.role != null) {
            conversationHistory.add(new Message(claudeResponse.role, finalResponse.toString()));
        } else {
            BoykaAILogger.warn("Received null role from Claude API");
        }
    }
    private String sendToolResultToClaude(String toolUseId, String toolResult, String url, String apiKey, String model) throws IOException {
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("model", model);

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");

        JsonArray content = new JsonArray();
        JsonObject toolResultObj = new JsonObject();
        toolResultObj.addProperty("type", "tool_result");
        toolResultObj.addProperty("tool_use_id", toolUseId);
        toolResultObj.addProperty("content", toolResult);
        content.add(toolResultObj);

        userMessage.add("content", content);
        messages.add(userMessage);

        jsonBody.add("messages", messages);

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonBody.toString());

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("sendToolResultToClaude Unexpected code " + response);
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            AIClaudeResponse claudeResponse = gson.fromJson(responseBody, AIClaudeResponse.class);

            // 返回 Claude 的响应文本
            return claudeResponse.content.get(0).text;
        }
    }
    private String sendToolResultToOpenAI(ToolCall toolCall, String toolResult, List<Tool> availableTools, String url, String apiKey, String model) throws IOException {
        JsonObject jsonBody = new JsonObject();
        jsonBody.addProperty("model", model);

        JsonArray messages = new JsonArray();
        for (Message message : conversationHistory) {
            JsonObject messageObject = new JsonObject();
            messageObject.addProperty("role", message.role);
            messageObject.addProperty("content", message.content);
            messages.add(messageObject);
        }

        JsonObject toolResultMessage = new JsonObject();
        toolResultMessage.addProperty("role", "function");
        toolResultMessage.addProperty("name", toolCall.function.name);
        toolResultMessage.addProperty("content", toolResult);
        messages.add(toolResultMessage);

        jsonBody.add("messages", messages);

        JsonArray toolsArray = new JsonArray();
        for (Tool tool : availableTools) {
            toolsArray.add(tool.toOpenAIFormat());
        }
        jsonBody.add("tools", toolsArray);

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonBody.toString());

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("content-type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            AIResponse openAIResponse = gson.fromJson(responseBody, AIResponse.class);

            // 返回 OpenAI 的响应文本
            return openAIResponse.choices[0].message.content;
        }
    }
    private void updateContextIfNeeded(ToolCall toolCall, String toolResult) {
        if (toolCall.function.name.equals("read_file") || toolCall.function.name.equals("create_file")) {
            JsonObject args = gson.fromJson(toolCall.function.arguments, JsonObject.class);
            String filePath = args.get("path").getAsString();
            contextManager.updateFileContent(filePath, toolResult);
        }
    }
    private String getConversationHistoryString() {
        StringBuilder history = new StringBuilder();
        for (Message message : conversationHistory) {
            history.append(message.role).append(": ").append(message.content).append("\n\n");
        }
        return history.toString();
    }

    public void clearConversationHistory() {
        conversationHistory.clear();
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
            JsonObject args = gson.fromJson(toolCall.function.arguments, JsonObject.class);
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

//    public void runAutoMode(String initialGoal, int maxIterations) {
//        for (int i = 0; i < maxIterations; i++) {
//            String iterationInfo = "You are currently on iteration " + (i + 1) + " out of " + maxIterations + " in automode.";
//            String context = contextManager.getFullContext();
//            String systemPrompt = BASE_SYSTEM_PROMPT + "\n\n" + AUTOMODE_SYSTEM_PROMPT.replace("{iteration_info}", iterationInfo);
//
//            List<Tool> availableTools = createAvailableTools();
//            String response = getAIResponse(initialGoal, "OpenAI", availableTools, context, settings.selectedModel);
//
//            // Process response, execute tool calls, etc.
//            if (response.contains("AUTOMODE_COMPLETE")) {
//                break;
//            }
//            initialGoal = "Continue with the next step.";
//        }
//    }

    private String getOpenAIAnalysis(String analysisPrompt, List<Tool> availableTools, String url, String apiKey, String model) throws IOException {
        AIRequest analysisRequest = new AIRequest(analysisPrompt, availableTools, model);
        RequestBody analysisBody = RequestBody.create(MediaType.parse("application/json"), gson.toJson(analysisRequest));

        Request analysisApiRequest = new Request.Builder()
                .url(url)
                .post(analysisBody)
                .addHeader("content-type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        try (Response analysisResponse = client.newCall(analysisApiRequest).execute()) {
            if (analysisResponse.isSuccessful()) {
                String analysisResponseBody = analysisResponse.body() != null ? analysisResponse.body().string() : "";
                AIResponse analysisAiResponse = gson.fromJson(analysisResponseBody, AIResponse.class);
                if (analysisAiResponse.choices != null && analysisAiResponse.choices.length > 0) {
                    return analysisAiResponse.choices[0].message.content;
                }
            }
            BoykaAILogger.error("Analysis API request failed", new IOException("Unexpected code " + analysisResponse.code()));
        }
        return "Failed to analyze the conversation and tool results.";
    }

    private String getClaudeAnalysis(String analysisPrompt, List<Tool> availableTools, String url, String apiKey, String model) throws IOException {
        AIRequest analysisRequest = new AIRequest(analysisPrompt, availableTools, model);
        RequestBody analysisBody = RequestBody.create(MediaType.parse("application/json"), gson.toJson(analysisRequest));

        Request analysisApiRequest = new Request.Builder()
                .url(url)
                .post(analysisBody)
                .addHeader("content-type", "application/json")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .build();

        try (Response analysisResponse = client.newCall(analysisApiRequest).execute()) {
            if (analysisResponse.isSuccessful()) {
                String analysisResponseBody = analysisResponse.body() != null ? analysisResponse.body().string() : "";
                AIClaudeResponse analysisClaudeResponse = gson.fromJson(analysisResponseBody, AIClaudeResponse.class);
                if (analysisClaudeResponse.content != null && !analysisClaudeResponse.content.isEmpty()) {
                    return analysisClaudeResponse.content.get(0).text;
                }
            }
            BoykaAILogger.error("Analysis API request failed", new IOException("Unexpected code " + analysisResponse.code()));
        }
        return "Failed to analyze the conversation and tool results.";
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

    private static class AIClaudeResponse {
        String id;
        String type;
        String role;
        String model;
        List<ContentBlock> content;
        String stop_reason;
        String stop_sequence;
        Usage usage;

        static class Usage {
            int input_tokens;
            int output_tokens;
        }
    }

    private static class ContentBlock {
        String type;
        String text;
        String id;
        String name;
        JsonObject input;
    }
    private static class AIResponse {
        String id;
        String object;
        long created;
        String model;
        Choice[] choices;
        Usage usage;
        String system_fingerprint;

        // Claude 特有字段
        String stop_reason;
        String role;

        // 新增内部类
        static class Usage {
            int prompt_tokens;
            int completion_tokens;
            int total_tokens;
        }
    }
    private static class Choice {
        int index;
        Message message;
        String logprobs;
        String finish_reason;

        // Claude 特有字段
        List<ContentBlock> content;
    }
    private static class Message {
        String role;
        String content;
        List<ToolCall> tool_calls;

        // 构造函数
        Message(String role, String content) {
            this.role = role;
            this.content = content;
            this.tool_calls = null;
        }

        // 带有 tool_calls 的构造函数
        Message(String role, String content, List<ToolCall> tool_calls) {
            this.role = role;
            this.content = content;
            this.tool_calls = tool_calls;
        }
    }
    private static class ToolCall {
        String id;
        String type;
        Function function;
    }
    private static class Function {
        String name;
        String arguments;
    }

}