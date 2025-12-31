# AI 服务配置说明

## 概述

NetPulse FX 支持多种 AI API 提供商，包括 DeepSeek、OpenAI 和本地 Ollama。

## 配置方式

### 方式一：环境变量（推荐）

在启动程序前设置以下环境变量：

#### DeepSeek API
```bash
# Windows (PowerShell)
$env:AI_PROVIDER="deepseek"
$env:AI_API_ENDPOINT="https://api.deepseek.com/v1/chat/completions"
$env:AI_API_KEY="your-deepseek-api-key"
$env:AI_MODEL="deepseek-chat"

# Linux/Mac
export AI_PROVIDER=deepseek
export AI_API_ENDPOINT=https://api.deepseek.com/v1/chat/completions
export AI_API_KEY=your-deepseek-api-key
export AI_MODEL=deepseek-chat
```

#### OpenAI API
```bash
# Windows (PowerShell)
$env:AI_PROVIDER="openai"
$env:AI_API_ENDPOINT="https://api.openai.com/v1/chat/completions"
$env:AI_API_KEY="your-openai-api-key"
$env:AI_MODEL="gpt-3.5-turbo"

# Linux/Mac
export AI_PROVIDER=openai
export AI_API_ENDPOINT=https://api.openai.com/v1/chat/completions
export AI_API_KEY=your-openai-api-key
export AI_MODEL=gpt-3.5-turbo
```

#### Ollama (本地)
```bash
# Windows (PowerShell)
$env:AI_PROVIDER="ollama"
$env:AI_API_ENDPOINT="http://localhost:11434/api/generate"
$env:AI_MODEL="llama2"

# Linux/Mac
export AI_PROVIDER=ollama
export AI_API_ENDPOINT=http://localhost:11434/api/generate
export AI_MODEL=llama2
```

**注意：** 如果未设置环境变量，程序默认使用 Ollama 本地配置。

## 使用 Ollama（本地部署）

1. 安装 Ollama：访问 https://ollama.ai 下载并安装
2. 下载模型（例如 llama2）：
   ```bash
   ollama pull llama2
   ```
3. 启动 Ollama 服务：
   ```bash
   ollama serve
   ```
4. 运行 NetPulse FX，程序会自动连接到本地 Ollama 服务

## API 端点参考

- **DeepSeek**: https://api.deepseek.com/v1/chat/completions
- **OpenAI**: https://api.openai.com/v1/chat/completions
- **Ollama**: http://localhost:11434/api/generate

## 注意事项

1. **API 密钥安全**：请妥善保管您的 API 密钥，不要将其提交到版本控制系统
2. **费用控制**：使用 DeepSeek 或 OpenAI 等云服务时请注意 API 调用费用
3. **网络连接**：确保网络连接正常，API 调用可能需要访问外网
4. **超时设置**：默认请求超时为 30 秒，如果网络较慢可能需要调整

## 测试配置

运行程序后，点击"AI 分析流量"按钮，如果配置正确，系统会：
1. 从数据库加载历史流量数据
2. 调用 AI API 进行分析
3. 在"AI 诊断报告"区域显示分析结果

如果出现错误，请检查：
- API 密钥是否正确
- 网络连接是否正常
- API 端点地址是否正确
- 如果是 Ollama，服务是否已启动




