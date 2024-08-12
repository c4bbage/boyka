package com.dobest1.boyka;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;

public class BoykaAIStatusBarWidget implements StatusBarWidget, StatusBarWidget.IconPresentation {
    private final Project project;

    public BoykaAIStatusBarWidget(Project project) {
        this.project = project;
    }

    @NotNull
    @Override
    public String ID() {
        return "BoykaAIWidget";
    }

    @Nullable
    @Override
    public WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    public void install(@NotNull StatusBar statusBar) {
    }

    @Override
    public void dispose() {
    }

    @Nullable
    @Override
    public String getTooltipText() {
        return "打开 Boyka AI 助手";
    }

    @Nullable
    @Override
    public Consumer<MouseEvent> getClickConsumer() {
        return mouseEvent -> ToolWindowManager.getInstance(project).getToolWindow("Boyka AI").show();
    }

    @NotNull
    @Override
    public Icon getIcon() {
        return com.intellij.openapi.util.IconLoader.getIcon("/icons/boyka_icon.png", BoykaAIStatusBarWidget.class);
    }
}