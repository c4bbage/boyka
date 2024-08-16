package com.dobest1.boyka;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class AddToContextAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        BoykaAILogger.info("Adding files to context");
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        if (files == null || files.length == 0) return;
        BoykaAILogger.info("contextManager Adding " + files.length + " files to context");
        ContextManager contextManager = ContextManager.getInstance(project);
        boolean contextChanged = false;
        for (VirtualFile file : files) {
            if (!contextManager.isFileInContext(file.getPath())) {
                BoykaAILogger.info("contextManager Adding " + file.getPath() + " to context");
                contextManager.addFileToContext(file.getPath());
                contextChanged = true;
            }
        }

        if (contextChanged) {
            contextManager.notifyContextChanged();
        }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);
        e.getPresentation().setEnabledAndVisible(project != null && files != null && files.length > 0);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}