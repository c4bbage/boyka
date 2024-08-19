package com.dobest1.boyka;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class ToolExecutor {
    private final BoykaAIFileTools fileTools;
    private final Gson gson;

    public ToolExecutor(BoykaAIFileTools fileTools) {
        this.fileTools = fileTools;
        this.gson = new Gson();
    }

    public String executeToolCall(String toolName, String arguments) {
        try {
            JsonObject args = gson.fromJson(arguments, JsonObject.class);
            switch (toolName) {
                case "create_file":
                    return fileTools.createFile(args.get("path").getAsString(), args.get("content").getAsString());
                case "create_folder":
                    return fileTools.createDirectory(args.get("path").getAsString());
                case "edit_and_apply":
                    return fileTools.editAndApply(
                            args.get("path").getAsString(),
                            args.get("instructions").getAsString(),
                            args.get("project_context").getAsString(),false,3
                    ).get();
                case "execute_code":
                    return fileTools.executeCode(args.get("code").getAsString());
                case "stop_process":
                    return fileTools.stopProcess(args.get("process_id").getAsString());
                case "read_file":
                    return fileTools.readFile(args.get("path").getAsString());
                case "read_multiple_files":
                    JsonArray pathsArray = args.getAsJsonArray("paths");
                    List<String> paths = new ArrayList<>();
                    pathsArray.forEach(element -> paths.add(element.getAsString()));
                    return String.join("\n\n", fileTools.readMultipleFiles(paths));
                case "list_files":
                    return String.join("\n", fileTools.listFiles(args.get("path").getAsString()));
                default:
                    return "Unknown tool call: " + toolName;
            }
        } catch (Exception e) {
            BoykaAILogger.error("Error in tool execution: "+toolName+arguments, e);
            return "An error occurred during <+"+toolName+">+tool execution:  "+ arguments+ "\nError msg: "+ e.getMessage();
        }
    }
}