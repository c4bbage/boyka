package com.dobest1.boyka;

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

// Added this import statement
import com.dobest1.boyka.BoykaAILogger;

public class BoykaAIFileTools {
    private final Project project;
    private final Path workingDirectory;

    public BoykaAIFileTools(Project project) {
        this.project = project;
        this.workingDirectory = Paths.get(project.getBasePath(), ".boykaai");
        try {
            Files.createDirectories(workingDirectory);
        } catch (IOException e) {
            // 使用日志记录错误
        }
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
        Path dirPath = workingDirectory.resolve(path);
        if (!isValidPath(dirPath)) {
            return Collections.singletonList("错误：无效的路径");
        }
        try {
            return Files.list(dirPath)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            return Collections.singletonList("错误：无法列出文件：" + e.getMessage());
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
            BoykaAILogger.error("读取文件失败", e);
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
    private void refreshFileSystem(@NotNull String path) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
        if (virtualFile != null) {
            virtualFile.refresh(false, true);
        }
    }

    public String editAndApply(String path, String instructions, String projectContext) {
        // 这个方法需要更复杂的实现，可能需要调用AI服务来处理编辑
        // 这里只是一个简单的占位实现
        return "编辑和应用功能尚未完全实现";
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