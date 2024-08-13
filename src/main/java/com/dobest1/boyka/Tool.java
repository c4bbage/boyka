package com.dobest1.boyka;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class Tool {
    private String name;
    private String description;
    private JsonObject inputSchema;
    private String[] required;

    public Tool(String name, String description, JsonObject inputSchema, String[] required) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.required = required;
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

    public String[] getRequired() {
        return required;
    }

    public JsonObject toOpenAIFormat() {
        JsonObject toolObject = new JsonObject();
        toolObject.addProperty("type", "function");

        JsonObject functionObject = new JsonObject();
        functionObject.addProperty("name", name);
        functionObject.addProperty("description", description);

        JsonObject parametersObject = new JsonObject();
        parametersObject.addProperty("type", "object");
        parametersObject.add("properties", inputSchema);
        if (required != null && required.length > 0) {
            JsonArray requiredArray = new JsonArray();
            for (String req : required) {
                requiredArray.add(req);
            }
            parametersObject.add("required", requiredArray);
        }
        functionObject.add("parameters", parametersObject);

        toolObject.add("function", functionObject);
        return toolObject;
    }

    public JsonObject toClaudeFormat() {
        JsonObject toolObject = new JsonObject();
        toolObject.addProperty("name", name);
        toolObject.addProperty("description", description);

        JsonObject schemaObject = new JsonObject();
        schemaObject.addProperty("type", "object");
        schemaObject.add("properties", inputSchema);
        if (required != null && required.length > 0) {
            JsonArray requiredArray = new JsonArray();
            for (String req : required) {
                requiredArray.add(req);
            }
            schemaObject.add("required", requiredArray);
        }
        toolObject.add("input_schema", schemaObject);

        return toolObject;
    }
}