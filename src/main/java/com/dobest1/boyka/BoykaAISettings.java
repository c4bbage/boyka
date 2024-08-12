package com.dobest1.boyka;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "BoykaAISettings",
        storages = {@Storage("BoykaAISettings.xml")}
)
public class BoykaAISettings implements PersistentStateComponent<BoykaAISettings.State> {
    public static class State {
        public String openAIAddress = "https://api.openai.com/v1/chat/completions";
        public String openAIKey = "";
        public String claudeAddress = "https://api.anthropic.com/v1/messages";
        public String claudeKey = "";
        public String selectedModel = "OpenAI";
        public boolean enableTools = true;
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