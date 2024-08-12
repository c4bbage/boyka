package com.dobest1.boyka;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class BoykaAIToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        BoykaAIToolWindowContent toolWindowContent = new BoykaAIToolWindowContent(project, toolWindow);
        Content content = ContentFactory.getInstance().createContent(toolWindowContent.getContent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}