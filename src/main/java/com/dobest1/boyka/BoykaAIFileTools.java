package com.dobest1.boyka;

import com.google.gson.Gson;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.reflect.TypeToken;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
// Added this import statement
import com.dobest1.boyka.BoykaAILogger;

public class BoykaAIFileTools {
    private final Project project;
    private final Path workingDirectory;
    private final Gson gson = new Gson();
    private final Map<String, String> fileContents = new HashMap<>();
    private final Set<String> codeEditorFiles = new HashSet<>();
    private final List<String> codeEditorMemory = new ArrayList<>();
    private final Map<String, Integer> codeEditorTokens = new HashMap<>();

    public BoykaAIFileTools(Project project  ) {
        this.project = project;
        codeEditorTokens.put("input", 0);
        codeEditorTokens.put("output", 0);
        this.workingDirectory = Paths.get(project.getBasePath());
    }

    private boolean isValidPath(Path path) {
        return path.startsWith(workingDirectory) && !path.toFile().isHidden();
    }

    private boolean isPathSafe(String path) {
        Path resolvedPath = workingDirectory.resolve(path).normalize();
        return resolvedPath.startsWith(workingDirectory);
    }

    public boolean createDirectory(String path) {
        Path dirPath = workingDirectory.resolve(path);
        if (!isValidPath(dirPath)) {
            return false;
        }
        try {
            Files.createDirectories(dirPath);
            return true;
        } catch (IOException e) {
            // 使用日志记录错误
            return false;
        }
    }

    public String createFile(String path, String content) {
        if (!isPathSafe(path)) {
            return "错误：尝试在不安全的路径创建文件";
        }
        Path filePath = workingDirectory.resolve(path);
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, content.getBytes());
            return "文件创建成功";
        } catch (IOException e) {
            return "文件创建失败：" + e.getMessage();
        }
    }

    public boolean writeFile(String path, String content) {
        Path filePath = workingDirectory.resolve(path);
        if (!isValidPath(filePath)) {
            return false;
        }
        try {
            Files.write(filePath, content.getBytes());
            return true;
        } catch (IOException e) {
            // 使用日志记录错误
            return false;
        }
    }

    public boolean deleteFileOrDirectory(String path) {
        Path dirPath = workingDirectory.resolve(path);
        if (!isValidPath(dirPath)) {
            return false;
        }
        try {
            Files.walk(dirPath)
                    .sorted((p1, p2) -> -p1.compareTo(p2))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            // 使用日志记录错误
                        }
                    });
            return true;
        } catch (IOException e) {
            // 使用日志记录错误
            return false;
        }
    }

    public boolean modifyFile(String path, String newContent) {
        return writeFile(path, newContent);
    }

    public List<String> listFiles(String path) {
        if (path==null || path.isEmpty()) {
            path = ".";
        }

        Path dirPath = workingDirectory.resolve(path);
        if (!isValidPath(dirPath)) {
            return Collections.singletonList("Error: 无效的路径"+dirPath);
        }
        try {
            return Files.list(dirPath)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.singletonList("Error: 无法列出文件：" + e.getMessage());
        }
    }

    public String readFile(String path) {
        Path filePath = workingDirectory.resolve(path);
        if (!isValidPath(filePath)) {
            return "";
        }
        try {
            return new String(Files.readAllBytes(filePath));
        } catch (IOException e) {
            // 使用日志记录错误
            BoykaAILogger.error("Error: 读取文件失败", e);
            return "";
        }
    }

    public List<String> readMultipleFiles(List<String> paths) {
        return paths.stream()
                .map(this::readFile)
                .collect(Collectors.toList());
    }

    public List<String> readFilesInDirectory(String directoryPath) {
        Path dirPath = workingDirectory.resolve(directoryPath);
        if (!isValidPath(dirPath)) {
            return new ArrayList<>();
        }
        try {
            return Files.walk(dirPath)
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        try {
                            return new String(Files.readAllBytes(path));
                        } catch (IOException e) {
                            // 使用日志记录错误
                            return "";
                        }
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            // 使用日志记���错误
            return new ArrayList<>();
        }
    }

    public List<String> searchFiles(String query) {
        List<String> results = new ArrayList<>();
        VirtualFile projectDir = project.getBaseDir();
        searchFilesRecursively(projectDir, query.toLowerCase(), results);
        return results;
    }

    private void searchFilesRecursively(VirtualFile dir, String query, List<String> results) {
        for (VirtualFile file : dir.getChildren()) {
            if (file.isDirectory()) {
                searchFilesRecursively(file, query, results);
            } else if (file.getName().toLowerCase().contains(query)) {
                results.add(file.getPath());
            }
        }
    }

    // 刷新 IntelliJ IDEA 的文件系统
    public  void refreshFileSystem(@NotNull String path) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
        if (virtualFile != null) {
            virtualFile.refresh(false, true);
        }
    }
    public CompletableFuture<String> generateEditInstructions(String filePath, String fileContent,
                                                              String instructions, String projectContext,
                                                              Map<String, String> fullFileContents) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder memoryContext = new StringBuilder();
                for (int i = 0; i < codeEditorMemory.size(); i++) {
                    memoryContext.append("Memory ").append(i + 1).append(":\n").append(codeEditorMemory.get(i)).append("\n");
                }

                StringBuilder fullFileContentsContext = new StringBuilder();
                for (Map.Entry<String, String> entry : fullFileContents.entrySet()) {
                    if (!entry.getKey().equals(filePath) || !codeEditorFiles.contains(entry.getKey())) {
                        fullFileContentsContext.append("--- ").append(entry.getKey()).append(" ---\n")
                                .append(entry.getValue()).append("\n\n");
                    }
                }

                String systemPrompt = String.format(
                        "You are an AI coding agent that generates edit instructions for code files. Your task is to analyze the provided code and generate SEARCH/REPLACE blocks for necessary changes. Follow these steps:\n\n" +
                                "1. Review the entire file content to understand the context:\n%s\n\n" +
                                "2. Carefully analyze the specific instructions:\n%s\n\n" +
                                "3. Take into account the overall project context:\n%s\n\n" +
                                "4. Consider the memory of previous edits:\n%s\n\n" +
                                "5. Consider the full context of all files in the project:\n%s\n\n" +
                                "6. Generate SEARCH/REPLACE blocks for each necessary change. Each block should:\n" +
                                "   - Include enough context to uniquely identify the code to be changed\n" +
                                "   - Provide the exact replacement code, maintaining correct indentation and formatting\n" +
                                "   - Focus on specific, targeted changes rather than large, sweeping modifications\n\n" +
                                "7. Ensure that your SEARCH/REPLACE blocks:\n" +
                                "   - Address all relevant aspects of the instructions\n" +
                                "   - Maintain or enhance code readability and efficiency\n" +
                                "   - Consider the overall structure and purpose of the code\n" +
                                "   - Follow best practices and coding standards for the language\n" +
                                "   - Maintain consistency with the project context and previous edits\n" +
                                "   - Take into account the full context of all files in the project\n\n" +
                                "IMPORTANT: RETURN ONLY THE SEARCH/REPLACE BLOCKS. NO EXPLANATIONS OR COMMENTS.\n" +
                                "USE THE FOLLOWING FORMAT FOR EACH BLOCK:\n\n" +
                                "<SEARCH>\n" +
                                "Code to be replaced\n" +
                                "</SEARCH>\n" +
                                "<REPLACE>\n" +
                                "New code to insert\n" +
                                "</REPLACE>\n\n" +
                                "If no changes are needed, return an empty list.",
                        fileContent, instructions, projectContext, memoryContext, fullFileContentsContext);

                String response = "";
                if (Objects.requireNonNull(BoykaAISettings.getInstance().getState()).enableClaude) {
                    ClaudeConfig claudeConfig = new ClaudeConfig.Builder()
                            .apiKey(BoykaAISettings.getInstance().getState().claudeKey)
                            .apiUrl(BoykaAISettings.getInstance().getState().claudeAddress)
                            .model(BoykaAISettings.getInstance().getState().claudeModel)
                            .build();
                    ClaudeClient claudeClient = new ClaudeClient(claudeConfig,systemPrompt);
                    response= claudeClient.sendMessageNoHistory(systemPrompt, "Generate SEARCH/REPLACE blocks for the necessary changes.", "",Collections.emptyList());
                } else {
                    OpenAIConfig openAIConfig = new OpenAIConfig.Builder()
                            .apiKey(BoykaAISettings.getInstance().getState().openAIKey)
                            .apiUrl(BoykaAISettings.getInstance().getState().openAIBaseAddress)
                            .model(BoykaAISettings.getInstance().getState().selectedModel)
                            .maxTokens(BoykaAISettings.getInstance().getState().maxTokens)
                            .build();
                    OpenAIClient openAIClient = new OpenAIClient(openAIConfig, systemPrompt);
                    response= openAIClient.sendMessage(systemPrompt, Collections.emptyList());
                }
                // Update token usage for code editor
                // Map<String, Integer> usage = aiService.getLastUsage();
                // codeEditorTokens.put("input", codeEditorTokens.get("input") + usage.get("input"));
                // codeEditorTokens.put("output", codeEditorTokens.get("output") + usage.get("output"));

                List<EditInstruction> editInstructions = parseSearchReplaceBlocks(response);
                BoykaAILogger.info("generateEditInstructions editInstructions: "+editInstructions.toString());
                // Update code editor memory
                codeEditorMemory.add("Edit Instructions for " + filePath + ":\n" + response);

                // Add the file to code_editor_files set
                codeEditorFiles.add(filePath);
                BoykaAILogger.info("generateEditInstructions: "+gson.toJson(editInstructions));
                return gson.toJson(editInstructions);
            } catch (Exception e) {
                BoykaAILogger.error("Error: in generating edit instructions", e);
                return "[]";
            }
        });
    }

    private List<EditInstruction> parseSearchReplaceBlocks(String responseText) {
        List<EditInstruction> blocks = new ArrayList<>();
        Pattern pattern = Pattern.compile("<SEARCH>\\s*(.*?)\\s*</SEARCH>\\s*<REPLACE>\\s*(.*?)\\s*</REPLACE>",
                Pattern.DOTALL);
        Matcher matcher = pattern.matcher(responseText);

        while (matcher.find()) {
            blocks.add(new EditInstruction(matcher.group(1).trim(), matcher.group(2).trim()));
        }

        return blocks;
    }

    public CompletableFuture<String> editAndApply(String path, String instructions, String projectContext,
                                                  boolean isAutomode, int maxRetries) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path filePath=workingDirectory.resolve(path);
                String originalContent = fileContents.getOrDefault(filePath, "");
                if (originalContent.isEmpty()) {
                    originalContent = new String(readFile(String.valueOf(filePath)));
                    fileContents.put(String.valueOf(filePath), originalContent);
                }
                StringBuilder currentInstructions = new StringBuilder(instructions);

                for (int attempt = 0; attempt < maxRetries; attempt++) {
                    String editInstructionsJson = generateEditInstructions(String.valueOf(filePath), originalContent, currentInstructions.toString(),
                            projectContext, fileContents).get();
                    BoykaAILogger.info("editAndApply: Edit instructions generated for " + path + ":\n" + editInstructionsJson);
                    if (!editInstructionsJson.equals("[]")) {
                        List<EditInstruction> editInstructions = gson.fromJson(editInstructionsJson,
                                new TypeToken<List<EditInstruction>>(){}.getType());
                        BoykaAILogger.info("editAndApply: Edit instructions parsed for " + path + ":\n" + editInstructions );
                        BoykaAILogger.info("Attempt " + (attempt + 1) + "/" + maxRetries +
                                ": The following SEARCH/REPLACE blocks have been generated:");
                        for (int i = 0; i < editInstructions.size(); i++) {
                            EditInstruction block = editInstructions.get(i);
                            System.out.println("Block " + (i + 1) + ":");
                            System.out.println("SEARCH:\n" + block.search + "\n\nREPLACE:\n" + block.replace);
                        }

                        ApplyEditsResult result = applyEdits(String.valueOf(filePath), editInstructions, originalContent);

                        if (result.changesMade) {
                            fileContents.put(String.valueOf(filePath), result.editedContent);
                            System.out.println("File contents updated: " + path);

                            if (!result.failedEdits.isEmpty()) {
                                System.out.println("Some edits could not be applied. Retrying...");
                                currentInstructions.append("\n\nPlease retry the following edits that could not be applied:\n")
                                        .append(String.join("\n", result.failedEdits));
                                originalContent = result.editedContent;
                                continue;
                            }

                            return "Changes applied to " + path;
                        } else if (attempt == maxRetries - 1) {
                            return "No changes could be applied to " + path + " after " + maxRetries +
                                    " attempts. Please review the edit instructions and try again.";
                        } else {
                            System.out.println("No changes could be applied in attempt " + (attempt + 1) + ". Retrying...");
                        }
                    } else {
                        return "No changes suggested for " + path;
                    }
                }

                return "Failed to apply changes to " + path + " after " + maxRetries + " attempts.";
            } catch (Exception e) {
                BoykaAILogger.error("Error editing/applying to file", e);
                return "Error editing/applying to file: " + e.getMessage();
            }
        });
    }

    private ApplyEditsResult applyEdits(String filePath, List<EditInstruction> editInstructions, String originalContent) {
        boolean changesMade = false;
        String editedContent = originalContent;
        List<String> failedEdits = new ArrayList<>();

        for (int i = 0; i < editInstructions.size(); i++) {
            EditInstruction edit = editInstructions.get(i);
            String searchContent = edit.search.trim();
            String replaceContent = edit.replace.trim();

            Pattern pattern = Pattern.compile(Pattern.quote(searchContent), Pattern.DOTALL);
            Matcher matcher = pattern.matcher(editedContent);

            if (matcher.find()) {
                editedContent = matcher.replaceFirst(Matcher.quoteReplacement(replaceContent));
                changesMade = true;
                System.out.println("Applied edit " + (i+1) + "/" + editInstructions.size());
            } else {
                System.out.println("Edit " + (i+1) + "/" + editInstructions.size() + " not applied: content not found");
                failedEdits.add("Edit " + (i+1) + ": " + searchContent);
            }
        }

        if (!changesMade) {
            System.out.println("No changes were applied. The file content already matches the desired state.");
        } else {
            try {
                Files.write(Paths.get(filePath), editedContent.getBytes());
                System.out.println("Changes have been written to " + filePath);
            } catch (IOException e) {
                BoykaAILogger.error("applyEdits: Error writing changes to file", e);
                System.err.println("Error writing changes to file: " + e.getMessage());
            }
        }

        return new ApplyEditsResult(editedContent, changesMade, failedEdits);
    }

    private static class EditInstruction {
        String search;
        String replace;

        EditInstruction(String search, String replace) {
            this.search = search;
            this.replace = replace;
        }
    }

    private static class ApplyEditsResult {
        String editedContent;
        boolean changesMade;
        List<String> failedEdits;

        ApplyEditsResult(String editedContent, boolean changesMade, List<String> failedEdits) {
            this.editedContent = editedContent;
            this.changesMade = changesMade;
            this.failedEdits = failedEdits;
        }
    }

    public String executeCode(String code) {
        // 这个方法需要一个安全的Python执行环境
        // 这里只是一个简单的占位实现
        return "代码执行功能尚未实现";
    }

    public String stopProcess(String processId) {
        // 这个方法需要与executeCode方法配合使用
        // 这里只是一个简单的占位实现
        return "停止进程功能尚未实现";
    }
}