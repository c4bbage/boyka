package com.dobest1.boyka;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.jetbrains.annotations.NotNull;

public class BoykaAIStatusBarWidgetFactory implements StatusBarWidgetFactory {
    @NotNull
    @Override
    public String getId() {
        return "BoykaAIWidget";
    }

    @NotNull
    @Override
    public StatusBarWidget createWidget(@NotNull Project project) {
        return new BoykaAIStatusBarWidget(project);
    }

    @Override
    public void disposeWidget(@NotNull StatusBarWidget widget) {
    }

    @NotNull
    @Override
    public String getDisplayName() {
        return "Boyka AI Assistant";
    }
}