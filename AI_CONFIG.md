# AI 服务配置说明

## 概述

NetPulse FX 支持多种 AI API 提供商，包括 **DeepSeek**、**Google Gemini**、**OpenAI** 和本地 **Ollama**。系统提供了两种配置方式：**图形界面配置（推荐）**和**环境变量配置**。

---

## 方式一：图形界面配置（推荐）✨

### 快速开始

1. **打开配置对话框**
   - 启动 NetPulse FX 后，点击主界面右上角的 **"配置 API"** 按钮
   - 或在主界面状态栏查看当前 AI 连接状态

2. **选择模型**
   - 在"模型名称"下拉框中选择您要使用的 AI 模型
   - 系统支持以下模型（按类别分类）：

   **DeepSeek 系列**（推荐）：
   - `[标准] DeepSeek Chat` - 标准对话模型（默认）
   - `[旗舰] DeepSeek V3` - 最新的深度思考模型，推理能力强大
   - `[推理] DeepSeek R1` - 专门优化的推理模型

   **Google Gemini 系列**：
   - `[旗舰] Gemini 2.5 Pro` - Google 最新的旗舰模型
   - `[极速] Gemini 2.5 Flash` - 快速响应版本，适合实时分析

   **OpenAI 系列**：
   - `[旗舰] GPT-4o` - 最新的多模态模型
   - `[极速] GPT-4o-mini` - 快速响应版本，成本更低

   **本地模型**：
   - `[本地] Llama 4` - Meta 开源模型（需要本地部署 Ollama）

   **自定义模型**：
   - `[自定义] 自定义模型` - 使用自定义 API 端点和模型名称

3. **填写 API 信息**
   - **API 接口**：选择模型后会自动填充默认端点，您也可以手动修改
   - **API Key**：输入您的 API 密钥
     - DeepSeek：从 [DeepSeek 官网](https://platform.deepseek.com/) 获取
     - Google Gemini：从 [Google AI Studio](https://makersuite.google.com/app/apikey) 获取
     - OpenAI：从 [OpenAI Platform](https://platform.openai.com/api-keys) 获取
     - Ollama：本地模型无需 API Key

4. **保存并测试**
   - 点击 **"保存且测试"** 按钮
   - 系统会自动测试连接，并在状态栏显示结果
   - 如果测试成功，配置会自动保存到本地文件

### 配置持久化

- **自动保存**：配置成功后，系统会自动将配置保存到本地文件
  - Windows: `%APPDATA%\NetPulseFX\ai_config.properties`
  - Linux/Mac: `~/.netpulsefx/ai_config.properties`
- **自动加载**：下次启动程序时，系统会自动加载保存的配置
- **无需重复输入**：配置一次后，后续使用无需重新输入 API Key

### 配置验证

配置成功后，您可以在主界面状态栏看到：
- ✅ **已连接{提供商名称}**（绿色）- 配置成功
- ❌ **未连接**（灰色）- 配置未完成或连接失败
- 🔵 **连接中...**（蓝色）- 正在测试连接

---

## 方式二：环境变量配置（高级）

如果您需要通过环境变量配置（例如在服务器环境或自动化脚本中），可以使用以下方式：

### DeepSeek API

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

### Google Gemini API

```bash
# Windows (PowerShell)
$env:AI_PROVIDER="gemini"
$env:AI_API_ENDPOINT="https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
$env:AI_API_KEY="your-gemini-api-key"
$env:AI_MODEL="gemini-2.5-flash"

# Linux/Mac
export AI_PROVIDER=gemini
export AI_API_ENDPOINT=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
export AI_API_KEY=your-gemini-api-key
export AI_MODEL=gemini-2.5-flash
```

### OpenAI API

```bash
# Windows (PowerShell)
$env:AI_PROVIDER="openai"
$env:AI_API_ENDPOINT="https://api.openai.com/v1/chat/completions"
$env:AI_API_KEY="your-openai-api-key"
$env:AI_MODEL="gpt-4o"

# Linux/Mac
export AI_PROVIDER=openai
export AI_API_ENDPOINT=https://api.openai.com/v1/chat/completions
export AI_API_KEY=your-openai-api-key
export AI_MODEL=gpt-4o
```

### Ollama (本地)

```bash
# Windows (PowerShell)
$env:AI_PROVIDER="ollama"
$env:AI_API_ENDPOINT="http://localhost:11434/api/generate"
$env:AI_MODEL="llama4"

# Linux/Mac
export AI_PROVIDER=ollama
export AI_API_ENDPOINT=http://localhost:11434/api/generate
export AI_MODEL=llama4
```

**注意：** 如果未设置环境变量，程序默认使用 **DeepSeek Chat** 配置。

---

## 支持的模型列表

### DeepSeek 系列

| 模型名称 | 模型 ID | API 端点 | 类别 |
|---------|---------|---------|------|
| DeepSeek Chat | `deepseek-chat` | `https://api.deepseek.com/v1/chat/completions` | 标准 |
| DeepSeek V3 | `deepseek-v3` | `https://api.deepseek.com/v1/chat/completions` | 旗舰 |
| DeepSeek R1 | `deepseek-r1` | `https://api.deepseek.com/v1/chat/completions` | 推理 |

### Google Gemini 系列

| 模型名称 | 模型 ID | API 端点 | 类别 |
|---------|---------|---------|------|
| Gemini 2.5 Pro | `gemini-2.5-pro` | `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent` | 旗舰 |
| Gemini 2.5 Flash | `gemini-2.5-flash` | `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent` | 极速 |

### OpenAI 系列

| 模型名称 | 模型 ID | API 端点 | 类别 |
|---------|---------|---------|------|
| GPT-4o | `gpt-4o` | `https://api.openai.com/v1/chat/completions` | 旗舰 |
| GPT-4o-mini | `gpt-4o-mini` | `https://api.openai.com/v1/chat/completions` | 极速 |

### Ollama 本地模型

| 模型名称 | 模型 ID | API 端点 | 类别 |
|---------|---------|---------|------|
| Llama 4 | `llama4` | `http://localhost:11434/api/generate` | 本地 |

---

## 使用 Ollama（本地部署）

### 安装步骤

1. **安装 Ollama**
   - 访问 [Ollama 官网](https://ollama.ai) 下载并安装
   - Windows: 下载安装程序并运行
   - Linux/Mac: 使用包管理器或下载二进制文件

2. **下载模型**
   ```bash
   ollama pull llama4
   # 或下载其他模型
   ollama pull llama3
   ollama pull mistral
   ```

3. **启动 Ollama 服务**
   ```bash
   ollama serve
   ```
   服务默认运行在 `http://localhost:11434`

4. **配置 NetPulse FX**
   - 打开"配置 API"对话框
   - 选择模型：`[本地] Llama 4`
   - API 接口会自动填充为：`http://localhost:11434/api/generate`
   - API Key 留空（Ollama 不需要 API Key）
   - 点击"保存且测试"

---

## API 端点参考

| 提供商 | API 端点 | 说明 |
|--------|---------|------|
| DeepSeek | `https://api.deepseek.com/v1/chat/completions` | 标准 Chat Completions API |
| Google Gemini | `https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent` | Google GenerateContent API |
| OpenAI | `https://api.openai.com/v1/chat/completions` | 标准 Chat Completions API |
| Ollama | `http://localhost:11434/api/generate` | 本地 Ollama 服务 |

---

## 错误处理

### Google Gemini 特定错误

系统已针对 Google Gemini API 的特殊错误进行了优化处理：

- **SAFETY（安全过滤）**：如果内容触发了 Google 的安全策略，系统会显示友好的中文错误提示
- **RECITATION（引用限制）**：如果内容可能包含受版权保护的内容，系统会提示调整请求内容

### 常见错误及解决方案

| 错误信息 | 可能原因 | 解决方案 |
|---------|---------|---------|
| **API 密钥无效或未设置（HTTP 401）** | API Key 错误或未填写 | 检查 API Key 是否正确，确保已填写 |
| **API 访问被拒绝（HTTP 403）** | 账户余额不足或权限受限 | 检查账户余额和 API 访问权限 |
| **请求频率过高（HTTP 429）** | API 调用频率超限 | 稍后重试，或检查 API 使用限额 |
| **无法连接到 API 服务器** | 网络连接问题 | 检查网络连接，确保可以访问外网 |
| **内容被安全过滤器阻止** | 输入内容触发安全策略 | 调整输入内容，避免敏感或不当信息 |

---

## 注意事项

### 安全性

1. **API 密钥安全**
   - 配置会自动保存到用户目录，不会提交到版本控制系统
   - API Key 使用 Base64 编码存储（非加密，仅避免明文）
   - 建议定期更换 API Key

2. **配置文件位置**
   - Windows: `%APPDATA%\NetPulseFX\ai_config.properties`
   - Linux/Mac: `~/.netpulsefx/ai_config.properties`
   - 请妥善保管配置文件，不要分享给他人

### 费用控制

1. **使用云服务时请注意 API 调用费用**
   - DeepSeek：按调用次数和 Token 数量计费
   - Google Gemini：按调用次数和 Token 数量计费
   - OpenAI：按调用次数和 Token 数量计费

2. **建议**
   - 定期检查 API 使用量
   - 设置 API 使用限额
   - 使用本地 Ollama 模型可避免云服务费用

### 网络连接

1. **确保网络连接正常**
   - API 调用需要访问外网（Ollama 除外）
   - 如果使用代理，请确保代理配置正确

2. **超时设置**
   - 默认请求超时为 30 秒
   - 会话分析超时为 90 秒（数据量大时）
   - 如果网络较慢，可能需要调整超时设置

---

## 测试配置

### 在 UI 中测试

1. 打开"配置 API"对话框
2. 填写配置信息
3. 点击"保存且测试"按钮
4. 查看状态栏显示的结果

### 在历史数据中测试

1. 打开"历史数据"窗口
2. 选择一个监控会话
3. 点击"AI 诊断"按钮
4. 如果配置正确，系统会：
   - 从数据库加载历史流量数据
   - 调用 AI API 进行分析
   - 在"AI 诊断报告"区域显示分析结果

### 故障排查

如果出现错误，请检查：
- ✅ API 密钥是否正确
- ✅ 网络连接是否正常
- ✅ API 端点地址是否正确
- ✅ 如果是 Ollama，服务是否已启动
- ✅ 账户余额是否充足
- ✅ API 使用限额是否超限

---

## 更新日志

### v1.0-SNAPSHOT

- ✨ 新增图形界面配置方式
- ✨ 新增配置持久化功能（自动保存和加载）
- ✨ 新增 Google Gemini 2.5 系列支持
- ✨ 新增 DeepSeek V3 和 R1 模型支持
- ✨ 新增 GPT-4o 和 GPT-4o-mini 支持
- ✨ 优化错误处理，特别是 Google Gemini 的安全过滤错误
- 🔄 移除过时模型（gpt-3.5-turbo, gpt-4, gpt-4-turbo, llama2, llama3, gemini-1.5 系列）
- 🎨 优化模型选择界面，添加类别标签
