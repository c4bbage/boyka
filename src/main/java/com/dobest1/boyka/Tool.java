package com.dobest1.boyka;

import com.google.gson.JsonObject;

public class Tool {
    private String name;
    private String description;
    private JsonObject inputSchema;

    public Tool(String name, String description, JsonObject inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public JsonObject getInputSchema() {
        return inputSchema;
    }
}