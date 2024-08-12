package com.dobest1.boyka;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.LocalFileSystem;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ContextManager {
    private final Project project;
    private final List<String> contextFiles;

    public ContextManager(Project project) {
        this.project = project;
        this.contextFiles = new ArrayList<>();
    }

    public void addFileToContext(String filePath) {
        if (!contextFiles.contains(filePath)) {
            contextFiles.add(filePath);
        }
    }

    public void removeFileFromContext(String filePath) {
        contextFiles.remove(filePath);
    }

    public List<String> getContextFiles() {
        return new ArrayList<>(contextFiles);
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
}