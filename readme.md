# Boyka AI Assistant

Boyka是一个强大的JetBrains IDE插件，旨在将AI辅助功能无缝集成到您的开发工作流程中。它利用先进的语言模型（如OpenAI的GPT和Anthropic的Claude）来提供智能代码建议、自动化任务和上下文相关的编程帮助。

## 特性

- **智能代码助手**: 利用AI生成代码、提供建议和解决编程问题。
- **多模型支持**: 集成了OpenAI和Claude等多个AI模型，可根据需求切换。
- **上下文感知**: 能够理解并利用项目上下文，提供更相关的建议。
- **高级文件操作**:
    - 自动创建文件夹和文件：根据项目需求，AI可以自动生成所需的目录结构和文件。
    - 智能文件编辑：AI可以根据指令自动修改现有文件，如添加新方法、更新注释或重构代码。
    - 批量文件处理：支持对多个文件同时进行AI辅助的修改和更新。
    - 文件内容分析：AI可以分析文件内容，提供优化建议或识别潜在问题。
- **自定义工具**: 可扩展的工具系统，支持添加自定义AI辅助功能。
- **流式输出**: 实时显示AI响应，提供更好的用户体验。
- **对话历史**: 保存并管理与AI的交互历史，便于回顾和继续之前的对话。

## 安装

1. 打开您的JetBrains IDE（如IntelliJ IDEA、PyCharm、GoLand等）。
2. 导航到 `Settings/Preferences` -> `Plugins`。
3. 点击 `Marketplace` 标签，搜索 "Boyka AI Assistant"。
4. 点击 `Install` 按钮。
5. 重启IDE以激活插件。

## 配置

首次使用时，您需要配置API密钥：

1. 导航到 `Settings/Preferences` -> `Tools` -> `Boyka AI Assistant`。
2. 输入您的OpenAI API密钥和/或Claude API密钥。
3. 选择默认使用的AI模型。
4. 根据需要调整其他设置（如最大令牌数、自动重复次数等）。

## 使用方法

1. 在IDE中，通过 `Tools` -> `Boyka AI Assistant` 打开Boyka窗口。
2. 在输入框中输入您的问题或指令。
3. 点击 `Send` 按钮或使用快捷键 `Ctrl+Enter` (Windows/Linux) 或 `Cmd+Enter` (Mac) 发送请求。
4. AI助手将在对话窗口中显示响应。
5. 使用 `Continue` 按钮继续之前的对话，或 `Clear` 按钮开始新的对话。

## 高级功能

- **上下文管理**: 使用上下文面板添加或移除项目文件，以提供更精确的AI辅助。
- **自动模式**: 启用自动模式，让AI连续工作，自动执行多个步骤。
- **智能文件操作**:
    - 通过简单的指令如"创建一个新的用户管理模块"，AI可以自动创建必要的文件夹结构和相关文件。
    - 要求AI"更新所有模型类，添加时间戳字段"，它会自动遍历并修改相关文件。
    - 使用"优化项目结构"指令，AI会分析当前项目结构并提供重组建议。

## 注意事项

- 确保您的API密钥安全，不要与他人分享。
- AI生成的代码和建议仅供参考，请始终进行代码审查和测试。
- 在使用自动文件操作功能时，建议先备份重要文件。

## 贡献

我们欢迎社区贡献！如果您有任何改进建议或发现了bug，请在GitHub上提交issue或pull request。

## 许可证

Boyka AI Assistant 遵循 [MIT 许可证](LICENSE)。

## 联系我们

如有任何问题或建议，请联系我们：support@boyka-ai.com

---

感谢您选择Boyka AI Assistant。我们希望这个工具能够显著提升您的开发效率和编程体验！