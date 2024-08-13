package com.dobest1.boyka;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;

public class BoykaAILogger {
    private static final Logger LOG = Logger.getInstance("BoykaAI");

    public static void info(String message) {
        LOG.info(message);
        showNotification("Info", message);
    }

    public static void warn(String message) {
        LOG.warn(message);
        showNotification("Warning", message);
    }

    public static void error(String message, Throwable e) {
        LOG.error(message, e);
        showNotification("Error", message + "\n" + e.getMessage());
    }

    private static void showNotification(String title, String message) {
        ApplicationManager.getApplication().invokeLater(() -> {
            Messages.showMessageDialog((Project) null, message, "BoykaAI " + title, Messages.getInformationIcon());
        });
    }
}