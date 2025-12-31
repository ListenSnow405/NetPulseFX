# AI 配置验证脚本
# 用于检查 DeepSeek API 环境变量配置是否正确

Write-Host "=== AI 配置验证 ===" -ForegroundColor Cyan
Write-Host ""

# 检查环境变量
$provider = $env:AI_PROVIDER
$endpoint = $env:AI_API_ENDPOINT
$apiKey = $env:AI_API_KEY
$model = $env:AI_MODEL

Write-Host "1. 环境变量检查:" -ForegroundColor Yellow
Write-Host "   AI_PROVIDER: " -NoNewline
if ($provider) {
    Write-Host "$provider" -ForegroundColor Green
} else {
    Write-Host "未设置" -ForegroundColor Red
}

Write-Host "   AI_API_ENDPOINT: " -NoNewline
if ($endpoint) {
    Write-Host "$endpoint" -ForegroundColor Green
} else {
    Write-Host "未设置" -ForegroundColor Red
}

Write-Host "   AI_API_KEY: " -NoNewline
if ($apiKey) {
    $keyLength = $apiKey.Length
    $keyPreview = if ($keyLength -gt 8) { $apiKey.Substring(0, 8) + "..." } else { "***" }
    Write-Host "已设置（长度: $keyLength, 预览: $keyPreview）" -ForegroundColor Green
} else {
    Write-Host "未设置" -ForegroundColor Red
}

Write-Host "   AI_MODEL: " -NoNewline
if ($model) {
    Write-Host "$model" -ForegroundColor Green
} else {
    Write-Host "未设置" -ForegroundColor Red
}

Write-Host ""

# 验证配置
Write-Host "2. 配置验证:" -ForegroundColor Yellow

$hasError = $false

if (-not $provider) {
    Write-Host "   ❌ AI_PROVIDER 未设置" -ForegroundColor Red
    $hasError = $true
} elseif ($provider -ne "deepseek" -and $provider -ne "openai" -and $provider -ne "ollama") {
    Write-Host "   ⚠️  AI_PROVIDER 值不正确（应为: deepseek, openai, 或 ollama）" -ForegroundColor Yellow
}

if (-not $endpoint) {
    Write-Host "   ❌ AI_API_ENDPOINT 未设置" -ForegroundColor Red
    $hasError = $true
} elseif ($provider -eq "deepseek" -and $endpoint -ne "https://api.deepseek.com/v1/chat/completions") {
    Write-Host "   ⚠️  DeepSeek 端点地址可能不正确" -ForegroundColor Yellow
    Write-Host "      当前: $endpoint" -ForegroundColor Yellow
    Write-Host "      应为: https://api.deepseek.com/v1/chat/completions" -ForegroundColor Yellow
}

if (-not $apiKey) {
    Write-Host "   ❌ AI_API_KEY 未设置（必需）" -ForegroundColor Red
    $hasError = $true
} elseif (-not $apiKey.StartsWith("sk-")) {
    Write-Host "   ⚠️  API Key 格式可能不正确（通常以 'sk-' 开头）" -ForegroundColor Yellow
}

if (-not $model) {
    Write-Host "   ⚠️  AI_MODEL 未设置（将使用默认值）" -ForegroundColor Yellow
}

Write-Host ""

# 测试网络连接
if ($endpoint) {
    Write-Host "3. 网络连接测试:" -ForegroundColor Yellow
    
    try {
        $uri = [System.Uri]$endpoint
        $hostname = $uri.Host
        
        Write-Host "   测试连接到: $hostname" -NoNewline
        
        $connection = Test-NetConnection -ComputerName $hostname -Port 443 -WarningAction SilentlyContinue -ErrorAction Stop
        
        if ($connection.TcpTestSucceeded) {
            Write-Host " ✓ 连接成功" -ForegroundColor Green
        } else {
            Write-Host " ✗ 连接失败" -ForegroundColor Red
            $hasError = $true
        }
    } catch {
        Write-Host " ✗ 无法解析域名: $($_.Exception.Message)" -ForegroundColor Red
        $hasError = $true
    }
}

Write-Host ""

# 总结
if ($hasError) {
    Write-Host "❌ 配置检查失败，请修复上述问题后重试" -ForegroundColor Red
    Write-Host ""
    Write-Host "快速设置示例（PowerShell）:" -ForegroundColor Cyan
    Write-Host '  $env:AI_PROVIDER="deepseek"'
    Write-Host '  $env:AI_API_ENDPOINT="https://api.deepseek.com/v1/chat/completions"'
    Write-Host '  $env:AI_API_KEY="sk-your-api-key-here"'
    Write-Host '  $env:AI_MODEL="deepseek-chat"'
    Write-Host ""
    Write-Host "然后重新运行此脚本验证配置。" -ForegroundColor Yellow
    exit 1
} else {
    Write-Host "✓ 配置检查通过！" -ForegroundColor Green
    Write-Host ""
    Write-Host "提示：这些环境变量只在当前 PowerShell 会话中有效。" -ForegroundColor Yellow
    Write-Host "如果通过 IDE 启动程序，可能需要在 IDE 的运行配置中设置环境变量。" -ForegroundColor Yellow
    exit 0
}




