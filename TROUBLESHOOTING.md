# AI 服务连接问题诊断指南

## 问题：无法连接到 DeepSeek API 服务器

如果您已经设置了环境变量但仍然无法连接，请按照以下步骤排查：

### 1. 验证环境变量设置

在 PowerShell 中运行以下命令验证环境变量：

```powershell
# 检查所有 AI 相关的环境变量
$env:AI_PROVIDER
$env:AI_API_ENDPOINT
$env:AI_API_KEY
$env:AI_MODEL
```

**重要提示**：环境变量设置只对当前 PowerShell 会话有效。如果：
- 在新的 PowerShell 窗口中运行程序，需要重新设置
- 通过 IDE 启动程序，环境变量可能不会被继承

### 2. 检查环境变量值

确保环境变量设置正确：

```powershell
# DeepSeek 配置示例（推荐方式：不使用引号）
$env:AI_PROVIDER="deepseek"
$env:AI_API_ENDPOINT="https://api.deepseek.com/v1/chat/completions"
$env:AI_API_KEY="sk-xxxxxxxxxxxxxxxxxxxxxxxx"  # 替换为您的实际 API Key
$env:AI_MODEL="deepseek-chat"

# 或者（PowerShell 也支持不带引号，但建议使用引号以防值中有空格）
$env:AI_PROVIDER=deepseek
$env:AI_API_ENDPOINT=https://api.deepseek.com/v1/chat/completions
$env:AI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
$env:AI_MODEL=deepseek-chat
```

**重要提示**：
- ✅ 端点地址必须完全正确：`https://api.deepseek.com/v1/chat/completions`
- ✅ API Key 必须以 `sk-` 开头
- ✅ **新版本已自动处理引号问题**：即使环境变量值中包含引号，代码也会自动清理
- ✅ 模型名称：`deepseek-chat` 或 `deepseek-coder`

**常见错误**：
- ❌ 如果遇到 "Illegal character in scheme name" 错误，说明环境变量值可能包含非法字符
- ✅ 新版本代码已修复此问题，会自动去除首尾的引号和空白字符

### 3. 测试网络连接

在 PowerShell 中测试能否访问 API 端点：

```powershell
# 测试 DeepSeek API 端点
Test-NetConnection -ComputerName api.deepseek.com -Port 443
```

如果无法连接，可能是：
- 网络防火墙阻止了 HTTPS 连接
- 需要使用代理服务器
- DNS 解析问题

### 4. 测试 API 连接（使用 curl）

使用 curl 测试 API 是否可访问：

```powershell
# 获取 API Key（从环境变量）
$apiKey = $env:AI_API_KEY

# 测试 API 调用
curl -X POST https://api.deepseek.com/v1/chat/completions `
  -H "Content-Type: application/json" `
  -H "Authorization: Bearer $apiKey" `
  -d '{
    "model": "deepseek-chat",
    "messages": [
      {"role": "user", "content": "Hello"}
    ]
  }'
```

如果 curl 测试成功但程序失败，可能是：
- 程序没有读取到环境变量
- Java SSL 证书问题
- 代理配置问题

### 5. 查看程序日志

运行程序后，查看控制台输出，应该看到类似以下信息：

```
[MainController] AI 服务已初始化（环境变量配置）:
  Provider: deepseek
  Endpoint: https://api.deepseek.com/v1/chat/completions
  Model: deepseek-chat
  API Key: 已设置（长度: XX）
```

如果看到 "使用默认配置（Ollama）"，说明环境变量没有被读取到。

### 6. 在 IDE 中设置环境变量

如果您使用 IntelliJ IDEA 或 Eclipse：

**IntelliJ IDEA**:
1. Run → Edit Configurations
2. 选择您的运行配置
3. Environment variables → 添加环境变量

**Eclipse**:
1. Run → Run Configurations
2. 选择您的配置
3. Environment 标签页 → 添加环境变量

### 7. 使用系统环境变量（持久化）

如果希望环境变量在所有会话中都有效：

**Windows**:
1. 右键"此电脑" → 属性
2. 高级系统设置 → 环境变量
3. 在"用户变量"或"系统变量"中添加：
   - `AI_PROVIDER` = `deepseek`
   - `AI_API_ENDPOINT` = `https://api.deepseek.com/v1/chat/completions`
   - `AI_API_KEY` = `your-api-key`
   - `AI_MODEL` = `deepseek-chat`

### 8. 常见错误和解决方案

#### 错误：Illegal character in scheme name / URISyntaxException

**可能原因**：
1. 环境变量值中包含引号（已修复：代码会自动清理）
2. 环境变量值中包含不可见字符
3. 端点地址格式不正确

**解决方案**：
- ✅ **已修复**：最新版本的代码会自动去除环境变量值中的引号和空白字符
- 如果仍遇到此问题，检查环境变量值：
  ```powershell
  # 检查环境变量值（注意是否有额外的引号）
  $env:AI_API_ENDPOINT
  
  # 重新设置（不带引号，或者代码会自动处理）
  $env:AI_API_ENDPOINT="https://api.deepseek.com/v1/chat/completions"
  ```

#### 错误：无法连接到 API 服务器

**可能原因**：
1. 端点地址错误
2. 网络连接问题
3. 防火墙阻止

**解决方案**：
- 检查端点地址是否正确
- 测试网络连接
- 检查防火墙设置

#### 错误：API 密钥无效（HTTP 401）

**可能原因**：
1. API Key 未设置或错误
2. API Key 已过期
3. API Key 格式不正确

**解决方案**：
- 确认环境变量 `AI_API_KEY` 已设置
- 在 DeepSeek 控制台重新生成 API Key
- 检查 API Key 是否以 `sk-` 开头

#### 错误：请求超时

**可能原因**：
1. 网络速度慢
2. API 服务器响应慢
3. 请求数据过大

**解决方案**：
- 检查网络连接速度
- 稍后重试
- 减少要分析的数据量

#### 错误：SSL/TLS 连接失败

**可能原因**：
1. Java 证书问题
2. 代理配置问题
3. 系统时间不正确

**解决方案**：
- 更新 Java 版本
- 配置代理（如果需要）
- 检查系统时间

### 9. 调试步骤

1. **确认环境变量被读取**：
   - 查看控制台输出的初始化日志
   - 确认所有配置都正确显示

2. **查看详细错误信息**：
   - 程序会在 AI 诊断报告中显示详细的错误信息
   - 检查控制台的错误日志

3. **测试简化请求**：
   - 先使用少量数据进行测试
   - 确认基本连接正常后再分析大量数据

### 10. 仍然无法解决？

如果以上步骤都无法解决问题，请提供以下信息：

1. 控制台的完整日志输出
2. 环境变量设置值（隐藏 API Key）
3. 错误信息的完整内容
4. 操作系统和 Java 版本
5. 网络环境（是否使用代理）

这样可以帮助进一步诊断问题。



