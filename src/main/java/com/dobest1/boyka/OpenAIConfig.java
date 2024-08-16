package com.dobest1.boyka;

public class OpenAIConfig {
    private final String apiKey;
    private final String apiUrl;
    private final String model;
    private final int maxTokens;
    private final int connectionTimeout;
    private final int readTimeout;
    private final int writeTimeout;

    private OpenAIConfig(Builder builder) {
        this.apiKey = builder.apiKey;
        this.apiUrl = builder.apiUrl;
        this.model = builder.model;
        this.maxTokens = builder.maxTokens;
        this.connectionTimeout = builder.connectionTimeout;
        this.readTimeout = builder.readTimeout;
        this.writeTimeout = builder.writeTimeout;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getModel() {
        return model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getWriteTimeout() {
        return writeTimeout;
    }

    public static class Builder {
        private String apiKey;
        private String apiUrl;
        private String model = "gpt-3.5-turbo";
        private int maxTokens = 1000;
        private int connectionTimeout = 120;
        private int readTimeout = 120;
        private int writeTimeout = 120;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder connectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder readTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder writeTimeout(int writeTimeout) {
            this.writeTimeout = writeTimeout;
            return this;
        }

        public OpenAIConfig build() {
            if (apiKey == null || apiUrl == null) {
                throw new IllegalStateException("API key and URL must be set");
            }
            return new OpenAIConfig(this);
        }
    }
}