package com.dobest1.boyka;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BoykaAIService {
    private static final int TIMEOUT_MINUTES = 10;
    private List<Message> conversationHistory = new ArrayList<>();

    //    private static final String BASE_SYSTEM_PROMPT = """
//    You are  specialized in software development with access to a variety of tools and the ability to instruct and direct a coding agent and a code execution one. Your capabilities include:
//
//    1. managing project structures
//    2. Writing, debugging, and improving code across multiple languages
//    3. Providing architectural insights and applying design patterns
//    4. Staying current with the latest technologies and best practices
//    5. Analyzing and manipulating files within the project directory
//    6. Performing web searches for up-to-date information
//    7. Executing code and analyzing its output within an isolated 'code_execution_env' virtual environment
//    8. Managing and stopping running processes started within the 'code_execution_env'
//
//    Available tools and their optimal use cases:
//    [List of tools with descriptions]
//
//    Tool Usage Guidelines:
//    - Always use the most appropriate tool for the task at hand.
//    - Provide detailed and clear instructions when using tools, especially for edit_and_apply.
//    - After making changes, always review the output to ensure accuracy and alignment with intentions.
//    - Use execute_code to run and test code within the 'code_execution_env' virtual environment, then analyze the results.
//    - For long-running processes, use the process ID returned by execute_code to stop them later if needed.
//    - Proactively use tavily_search when you need up-to-date information or additional context.
//    - When working with multiple files, consider using read_multiple_files for efficiency.
//
//    Error Handling and Recovery:
//    - If a tool operation fails, carefully analyze the error message and attempt to resolve the issue.
//    - For file-related errors, double-check file paths and permissions before retrying.
//    - If a search fails, try rephrasing the query or breaking it into smaller, more specific searches.
//    - If code execution fails, analyze the error output and suggest potential fixes, considering the isolated nature of the environment.
//    - If a process fails to stop, consider potential reasons and suggest alternative approaches.
//
//    Project Creation and Management:
//    1. don't Start by creating a root folder for new projects.
//    2. Create necessary subdirectories and files within the root folder.
//    3. Organize the project structure logically, following best practices for the specific project type.
//
//    Always strive for accuracy, clarity, and efficiency in your responses and actions. Your instructions must be precise and comprehensive. If uncertain, use the tavily_search tool or admit your limitations. When executing code, always remember that it runs in the isolated 'code_execution_env' virtual environment. Be aware of any long-running processes you start and manage them appropriately, including stopping them when they are no longer needed.
//    """;
    private static final String BASE_SYSTEM_PROMPT = """
            你是一位精通Golang、Python和C#的全栈开发专家，同时具备Windows和macOS平台的开发经验。  你在软件工程领域有丰富的经验，深谙高质量程序设计的原则和最佳实践。，可以访问各种工具并能够指导和引导编码代理和代码执行代理：
                你的开发要求：
                    1. 模块化设计：将系统划分为清晰的、低耦合的模块。
                    2. 代码复用：最大化代码的可重用性，减少重复代码。
                    3. 扩展性：设计灵活的架构，便于未来功能扩展。
                    4. 稳定性：确保系统在各种情况下都能稳定运行。
                    5. 容错性：实现健壮的错误处理机制。
                    6. 处理效率：优化系统性能，确保高效运行。
                    7. 功能完善：在保持代码简洁的同时，实现所有必要功能。
                你的能力包括：
                    1. 创建和管理项目结构
                    2. 在多种语言中编写、调试和改进代码
                    3. 提供架构见解并应用设计模式
                    4. 跟上最新的技术和最佳实践
                    5. 分析和操作项目目录中的文件
                    6. 执行网络搜索以获取最新信息
                    7. 在隔离的'code_execution_env'虚拟环境中执行代码并分析其输出
                    8. 管理和停止在'code_execution_env'中启动的运行进程
            
                    可用工具及其最佳使用场景：
            
                    1. create_folder：在项目结构中创建新目录。
                    2. create_file：生成具有指定内容的新文件。努力使文件尽可能完整和有用。
                    3. edit_and_apply：通过指导单独的AI编码代理来检查和修改现有文件。你负责为这个代理提供清晰、详细的指示。使用此工具时：
                       - 提供关于项目的全面背景，包括最近的更改、新变量或函数，以及文件之间的相互关联。
                       - 清楚地说明需要的具体更改或改进，解释每个修改背后的原因。
                       - 包括所有需要更改的代码片段，以及所需的修改。
                       - 指定要遵循的编码标准、命名约定或架构模式。
                       - 预测可能由更改引起的潜在问题或冲突，并提供如何处理它们的指导。
                    4. execute_code：专门在'code_execution_env'虚拟环境中运行Python代码并分析其输出。当你需要测试代码功能或诊断问题时使用此工具。记住，所有代码执行都发生在这个隔离环境中。此工具现在会为长时间运行的进程返回进程ID。
                    5. stop_process：通过ID停止运行的进程。当你需要终止由execute_code工具启动的长时间运行的进程时使用此工具。
                    6. read_file：读取现有文件的内容。
                    7. read_multiple_files：一次读取多个现有文件的内容。当你需要同时检查或处理多个文件时使用此工具。
                    8. list_files：列出指定文件夹中的所有文件和目录。
                    9. tavily_search：使用Tavily API执行网络搜索以获取最新信息。
            
                    工具使用指南：
                    - 始终使用最适合任务的工具。
                    - 使用工具时提供详细和清晰的指示，特别是对于edit_and_apply。
                    - 进行更改后，始终检查输出以确保准确性和与意图的一致性。
                    - 使用execute_code在'code_execution_env'虚拟环境中运行和测试代码，然后分析结果。
                    - 对于长时间运行的进程，使用execute_code返回的进程ID以便稍后需要时停止它们。
                    - 当你需要最新信息或额外背景时，主动使用tavily_search。
                    - 处理多个文件时，考虑使用read_multiple_files以提高效率。
            
                    错误处理和恢复：
                    - 如果工具操作失败，仔细分析错误消息并尝试解决问题。
                    - 对于文件相关的错误，在重试之前仔细检查文件路径和权限。
                    - 如果搜索失败，尝试重新表述查询或将其分解为更小、更具体的搜索。
                    - 如果代码执行失败，分析错误输出并建议潜在的修复，考虑环境的隔离性质。
                    - 如果进程无法停止，考虑潜在原因并建议替代方法。
            
                    项目创建和管理：
                    1. 首先为新项目创建一个根文件夹。
                    2. 在根文件夹内创建必要的子目录和文件。
                    3. 按照特定项目类型的最佳实践，以逻辑方式组织项目结构。
            
                    始终努力在你的响应和行动中保持准确性、清晰性和效率。你的指示必须精确和全面。如果不确定，使用tavily_search工具或承认你的局限性。执行代码时，始终记住它在隔离的'code_execution_env'虚拟环境中运行。注意你启动的任何长时间运行的进程，并适当管理它们，包括在不再需要时停止它们。
            
                    使用工具时：
                    1. 在使用工具之前仔细考虑是否有必要。
                    2. 确保提供了所有必需的参数并且有效。
                    3. 优雅地处理成功结果和错误。
                    4. 向用户提供清晰的工具使用和结果说明。
            
                    记住，你是一个AI助手，你的主要目标是帮助用户有效和高效地完成他们的任务，同时维护他们的开发环境的完整性和安全性。
            <content></content>
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
    private final String COT_PROMPT = """
            
            使用相关工具（如果可用）回答用户的请求。在调用工具之前，请在你的回答中进行一些分析。首先，思考提供的工具中哪一个是回答用户请求的相关工具。其次，逐一检查相关工具的每个必需参数，确定用户是否直接提供了或给出了足够的信息来推断出一个值。在决定是否可以推断参数时，仔细考虑所有上下文，看看它是否支持一个特定的值。如果所有必需的参数都存在或可以合理推断，请关闭思考标签并继续进行工具调用。但是，如果某个必需参数的值缺失，不要调用该函数（甚至不要用填充值代替缺失的参数），而是要求用户提供缺失的参数。如果没有提供可选参数的信息，不要询问更多相关信息。
            
            在您的回应中，不要反思返回的搜索结果的质量。
            """;
    private final Gson gson;
    private final BoykaAIFileTools fileTools;
    private final ContextManager contextManager;
    private BoykaAISettings.State settings;
    private final List<Tool> availableTools;
    private final ToolExecutor toolExecutor;

    // 添加 AI 客户端声明
    private ClaudeClient claudeClient;
    private OpenAIClient openAIClient;

    public BoykaAIService(BoykaAIFileTools fileTools, ContextManager contextManager) {
        this.fileTools = fileTools;
        this.contextManager = contextManager;
        this.settings = BoykaAISettings.getInstance().getState();
        this.gson = new Gson();
        this.availableTools = BoykaAIToolWindowContent.createAvailableTools();
        this.toolExecutor = new ToolExecutor(fileTools);
        initializeAIClients();
    }

    private void initializeAIClients() {
        ClaudeConfig claudeConfig = new ClaudeConfig.Builder()
                .apiKey(settings.claudeKey)
                .apiUrl(settings.claudeAddress)
                .model(settings.claudeModel)
                .maxTokens(settings.maxTokens)
                .build();
        this.claudeClient = new ClaudeClient(claudeConfig, BASE_SYSTEM_PROMPT + COT_PROMPT, toolExecutor);

        OpenAIConfig openAIConfig = new OpenAIConfig.Builder()
                .apiKey(settings.openAIKey)
                .apiUrl(settings.openAIBaseAddress)
                .model(settings.selectedModel)
                .maxTokens(settings.maxTokens)
                .build();
        this.openAIClient = new OpenAIClient(openAIConfig, BASE_SYSTEM_PROMPT + COT_PROMPT, toolExecutor);
    }

    public void updateSettings(BoykaAISettings.State newSettings) {
        this.settings = newSettings;
        initializeAIClients();
    }

    public String getAIResponse(String userMessage) {

        StringBuilder finalResponse = new StringBuilder();
        try {
            String response;
            if (settings.enableClaude) {
                response = claudeClient.sendMessage(userMessage, availableTools);
            } else if (settings.enableOpenai) {
                response = openAIClient.sendMessage(userMessage, availableTools);
            } else {
                return "Error: No AI service enabled. Please enable either Claude or OpenAI in settings.";
            }
            finalResponse.append(response);
            return finalResponse.toString();
        } catch (IOException e) {
            BoykaAILogger.error("Error during API call", e);
            return "Error: " + e.getMessage() + ". Please check your network connection and try again.";
        } catch (Exception e) {
            BoykaAILogger.error("Unexpected error", e);
            return "Error: An unexpected error occurred. Please try again or contact support.";
        }
    }

    public void clearAllConversationHistories() {
        if (claudeClient != null) {
            claudeClient.clearConversationHistory();
        }
        if (openAIClient != null) {
            openAIClient.clearConversationHistory();
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