package com.dobest1.boyka;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
        name = "BoykaAISettings",
        storages = {@Storage("BoykaAISettings.xml")}
)
public class BoykaAISettings implements PersistentStateComponent<BoykaAISettings.State> {
    public static class State {
        public String openAIBaseAddress = "http://192.168.135.64:3000/";
        public String openAIKey = "sk-c7Ykbdq8AVQej8evD375058d1bA74993B9807fDb9cB7Db60";
        public String claudeAddress = "https://lucky-firefly-c4ef.aardhard897.workers.dev/";  // 设置默认值
        public String claudeKey = "";
        public String selectedModel = "gpt-3.5-turbo";
        public String claudeModel = "claude-3-5-sonnet";
        public int maxTokens = 4000;
        public List<String> availableModels = new ArrayList<>();
        public boolean enableClaude = false;
        public boolean enableOpenai = false;
    }

    private State myState = new State();

    public static BoykaAISettings getInstance() {
        return ServiceManager.getService(BoykaAISettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }
}