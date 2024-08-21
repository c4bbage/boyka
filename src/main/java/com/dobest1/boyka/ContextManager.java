package com.dobest1.boyka;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ContextManager {
    private final BoykaAISettings settings;
    private final List<String> contextFiles;
    private final List<ContextChangeListener> listeners;
    private final ConcurrentHashMap<String, String> fileContents;
    private final Project project;
    private String cachedContext;
    private long lastUpdateTime;
    private static final long CACHE_VALIDITY_PERIOD = 5000; // 5 seconds
    public static ContextManager getInstance(Project project) {
        return ServiceManager.getService(project,ContextManager.class);
    }

    public ContextManager(Project project) {
        this.project = project;
        this.contextFiles = new CopyOnWriteArrayList<>();
        this.listeners = new ArrayList<>();
        this.fileContents = new ConcurrentHashMap<>();
        this.settings = BoykaAISettings.getInstance();

    }

    public void addFileToContext(String filePath) {
        if (!contextFiles.contains(filePath)) {
            contextFiles.add(filePath);
            BoykaAILogger.info("File added to context: " + filePath);
            notifyContextChanged();
        }
        saveContext();
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
        saveContext();
    }

    public void saveContext() {
        BoykaAISettings.State settings = BoykaAISettings.getInstance().getState();
        assert settings != null;
        settings.projectContexts = getFullContext();
        BoykaAILogger.info("Context saved: " + settings.projectContexts);
    }

    public List<String> searchProjectFiles(String query) {
        List<String> results = new ArrayList<>();
        VirtualFile projectDir = ProjectManager.getInstance().getOpenProjects()[0].getBaseDir();
        if (projectDir == null) {
            return results;
        }
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
        saveContext();
        return null;
    }

    // 在任何更新上下文的方法中调用 invalidateCache()
    public void updateFileContent(String filePath, String content) {
        fileContents.put(filePath, content);
        invalidateCache();
    }

    public String getFileContent(String filePath) {
        return fileContents.getOrDefault(filePath, "");
    }


    public synchronized String getFullContext() {
        long currentTime = System.currentTimeMillis();
        if (cachedContext == null || (currentTime - lastUpdateTime) > CACHE_VALIDITY_PERIOD) {
            StringBuilder context = new StringBuilder();
            for (String filePath : contextFiles) {
                context.append("File: ").append(filePath).append("\n");
                String content = fileContents.get(filePath);
                if (content == null) {
                    content = readFileContent(filePath);
                    if (content != null) {
                        fileContents.put(filePath, content);
                    }
                }
                context.append(content != null ? content : "File content not available").append("\n\n");
            }
            cachedContext = context.toString();
            lastUpdateTime = currentTime;
        }
        return cachedContext;
    }

    public synchronized void invalidateCache() {
        cachedContext = null;
    }


    private String readFileContent(String filePath) {
        try {
            return new String(Files.readAllBytes(Paths.get(filePath)));
        } catch (IOException e) {
            BoykaAILogger.error("Error reading file: " + filePath, e);
            return null;
        }
    }
}