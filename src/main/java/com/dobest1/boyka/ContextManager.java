package com.dobest1.boyka;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import java.util.concurrent.CopyOnWriteArrayList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ContextManager {
    private final Project project;
    private final List<String> contextFiles;
    private final List<ContextChangeListener> listeners;
    private final ConcurrentHashMap<String, String> fileContents;

    public static ContextManager getInstance(Project project) {
        return ServiceManager.getService(project, ContextManager.class);
    }

    public ContextManager(Project project) {
        this.project = project;
        this.contextFiles = new CopyOnWriteArrayList<>();
        this.listeners = new ArrayList<>();
        this.fileContents = new ConcurrentHashMap<>();

    }

    public void addFileToContext(String filePath) {
        if (!contextFiles.contains(filePath)) {
            contextFiles.add(filePath);
            BoykaAILogger.info("File added to context: " + filePath);
            notifyContextChanged();
        }
    }

    public boolean isFileInContext(String filePath) {
        return contextFiles.contains(filePath);
    }

    public List<String> getContextFiles() {
        return new ArrayList<>(contextFiles);
    }

    public void addContextChangeListener(ContextChangeListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeContextChangeListener(ContextChangeListener listener) {
        listeners.remove(listener);
    }

    public void notifyContextChanged() {
        BoykaAILogger.info("Notifying context change to " + listeners.size() + " listeners");
        for (ContextChangeListener listener : listeners) {
            listener.onContextChanged();
        }
    }

    public interface ContextChangeListener {
        void onContextChanged();
    }


    public void removeFileFromContext(String filePath) {
        contextFiles.remove(filePath);
    }


    public List<String> searchProjectFiles(String query) {
        List<String> results = new ArrayList<>();
        VirtualFile projectDir = project.getBaseDir();
        searchFiles(projectDir, query, results);
        return results;
    }

    private void searchFiles(VirtualFile dir, String query, List<String> results) {
        for (VirtualFile file : dir.getChildren()) {
            if (file.isDirectory()) {
                searchFiles(file, query, results);
            } else if (file.getName().toLowerCase().contains(query.toLowerCase())) {
                results.add(file.getPath());
            }
        }
    }

    public String addExternalFile(String filePath) {
        Path path = Paths.get(filePath);
        if (path.toFile().exists()) {
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath);
            if (file != null) {
                addFileToContext(file.getPath());
                return file.getPath();
            }
        }
        return null;
    }
    public void updateFileContent(String filePath, String content) {
        fileContents.put(filePath, content);
    }

    public String getFileContent(String filePath) {
        return fileContents.getOrDefault(filePath, "");
    }


    public String getFullContext() {
        StringBuilder context = new StringBuilder();
        for (String filePath : contextFiles) {
            context.append("File: ").append(filePath).append("\n");
            context.append(fileContents.get(filePath)).append("\n\n");
        }
        return context.toString();
    }
}