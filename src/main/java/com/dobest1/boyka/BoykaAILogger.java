package com.dobest1.boyka;

import com.intellij.openapi.diagnostic.Logger;

public class BoykaAILogger {
    private static final Logger LOG = Logger.getInstance("BoykaAI");

    public static void info(String message) {
        LOG.info(message);
    }

    public static void warn(String message) {
        LOG.warn(message);
    }

    public static void error(String message, Throwable e) {
        LOG.error(message, e);
    }
}