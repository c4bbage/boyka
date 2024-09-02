# Boyka AI Assistant

Boyka is a powerful JetBrains IDE plugin designed to seamlessly integrate AI-assisted functionalities into your development workflow. It leverages advanced language models (such as OpenAI's GPT and Anthropic's Claude) to provide intelligent code suggestions, automate tasks, and offer context-aware programming assistance.

## Features

- **Intelligent Code Assistant**: Utilizes AI to generate code, provide suggestions, and solve programming problems.
- **Multi-Model Support**: Integrates multiple AI models including OpenAI and Claude, allowing switching based on needs.
- **Context-Aware**: Understands and utilizes project context to provide more relevant suggestions.
- **Advanced File Operations**:
  - Automatic folder and file creation: AI can automatically generate required directory structures and files based on project needs.
  - Intelligent file editing: AI can automatically modify existing files based on instructions, such as adding new methods, updating comments, or refactoring code.
  - Batch file processing: Supports AI-assisted modifications and updates to multiple files simultaneously.
  - File content analysis: AI can analyze file contents, provide optimization suggestions, or identify potential issues.
- **Custom Tools**: Extensible tool system supporting the addition of custom AI-assisted functionalities.
- **Streaming Output**: Displays AI responses in real-time, providing a better user experience.
- **Conversation History**: Saves and manages interaction history with AI, facilitating review and continuation of previous conversations.

## Installation

1. Open your JetBrains IDE (e.g., IntelliJ IDEA, PyCharm, GoLand, etc.).
2. Navigate to `Settings/Preferences` -> `Plugins`.
3. Click on the `Marketplace` tab and search for "Boyka AI Assistant".
4. Click the `Install` button.
5. Restart the IDE to activate the plugin.

## Configuration

On first use, you need to configure API keys:

1. Navigate to `Settings/Preferences` -> `Tools` -> `Boyka AI Assistant`.
2. Enter your OpenAI API key and/or Claude API key.
3. Select the default AI model to use.
4. Adjust other settings as needed (e.g., maximum tokens, auto-repeat count, etc.).

## How to Use

1. In the IDE, open the Boyka window via `Tools` -> `Boyka AI Assistant`.
2. Enter your question or instruction in the input box.
3. Click the `Send` button or use the shortcut `Ctrl+Enter` (Windows/Linux) or `Cmd+Enter` (Mac) to send the request.
4. The AI assistant will display the response in the conversation window.
5. Use the `Continue` button to continue previous conversations, or the `Clear` button to start a new conversation.

## Advanced Features

- **Context Management**: Use the context panel to add or remove project files for more precise AI assistance.
- **Auto Mode**: Enable auto mode to let AI work continuously, automatically executing multiple steps.
- **Intelligent File Operations**:
  - AI can automatically create necessary folder structures and related files with simple instructions like "Create a new user management module".
  - Request AI to "Update all model classes, add timestamp fields", and it will automatically traverse and modify relevant files.
  - Use the "Optimize project structure" command, and AI will analyze the current project structure and provide reorganization suggestions.

## Notes

- Ensure your API key is secure and not shared with others.
- AI-generated code and suggestions are for reference only. Always perform code review and testing.
- When using automatic file operation features, it's recommended to back up important files first.

## Contribution

We welcome community contributions! If you have any suggestions for improvements or have found a bug, please submit an issue or pull request on GitHub.

## License

Boyka AI Assistant is licensed under the [MIT License](LICENSE).

## Contact Us

For any questions or suggestions, please contact us at: c4bbage@live.com

---

Thank you for choosing Boyka AI Assistant. We hope this tool significantly enhances your development efficiency and programming experience!