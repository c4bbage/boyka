package com.dobest1.boyka;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 存储 AI 相关的配置信息，用于在 IntelliJ IDEA 中的设置中进行配置。
 *
 * @see State：存储配置信息的类，包含了各种字段，例如 OpenAI 和 Claude 的服务地址、API 密钥、选择的模型、最大
 * 允许的 token 数量等等。
 * @see PersistentStateComponent：IntelliJ IDEA 的一个接口，用于实现可持久化的组件，即组件的状态可以保存到磁盘上，
 * 并且可以在重新启动 IDEA 后恢复。
 */
@State(
        name = "BoykaAISettings",
        storages = {@Storage("BoykaAISettings.xml")}
)
public class BoykaAISettings implements PersistentStateComponent<BoykaAISettings.State> {
    public static class State {
        public String projectBasePath = "";
        public String projectContexts = "";
        /**
         * OpenAI 的服务地址
         */
        public String openAIBaseAddress = "http://192.168.135.64:3000/v1/";
        /**
         * OpenAI 的 API 密钥
         */
        public String openAIKey = "sk-c7Ybdq8AVQej8evD375058d1bA74993B9807fDb9cB7Db60";
        /**
         * Claude 的服务地址
         */
        public String claudeAddress = "https://lucky-firefly-c4ef.aardhard897.workers.dev/v1/";  // 设置默认值
        /**
         * Claude 的 API 密钥
         */
        public String claudeKey = "";
        /**
         * 选择的模型，默认为 "gpt-3.5-turbo"
         */
        public String selectedModel = "gpt-3.5-turbo";
        /**
         * Claude 模型，默认为 "claude-3-5-sonnet"
         */
        public String claudeModel = "claude-3-5-sonnet";
        /**
         * 最大允许的 token 数量，默认为 4000
         */
        public int maxTokens = 4000;
        /**
         * 可用的模型列表，默认为空列表
         */
        public List<String> availableModels = new ArrayList<>();
        /**
         * 是否启用 Claude，默认为 false
         */
        public boolean enableClaude = false;
        /**
         * 是否启用 OpenAI，默认为 false
         */
        public boolean enableOpenai = false;
        /**
         * 自动重复次数，默认为 10
         */
        public int autoRepeatCount = 10;

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
        if (state.autoRepeatCount > 10) {
            state.autoRepeatCount = 10;
        }
        myState = state;
    }
}