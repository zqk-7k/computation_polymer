# Ollama 大模型启动与关闭说明

更新时间：2026-05-28

## 1. 当前部署方式

```text
浏览器 /assistant
  -> Spring Boot 后端 /api/assistant/chat
  -> Ollama /api/chat
  -> qwen3:8b
```

开发阶段：后端在 Windows，Ollama 在 Linux，Windows 通过 SSH 隧道访问 `127.0.0.1:11434`。

正式 Linux 部署阶段：Spring Boot 与 Ollama 同机运行，后端直接访问 `http://127.0.0.1:11434`，不公开 Ollama 端口。

## 2. 登录服务器

```powershell
ssh -p 2338 root@gpu.chzmark.com
```

## 3. 启动 Ollama

```bash
export PATH=/root/autodl-tmp/qkzhang/ollama/bin:$PATH
export OLLAMA_HOST=127.0.0.1:11434
```

如果已有守护脚本：

```bash
mkdir -p /root/autodl-tmp/qkzhang/logs
nohup bash /root/autodl-tmp/qkzhang/scripts/ollama-watch.sh \
  > /root/autodl-tmp/qkzhang/logs/ollama-watch.log \
  2>&1 &
```

如果脚本不存在，可以创建：

```bash
mkdir -p /root/autodl-tmp/qkzhang/scripts /root/autodl-tmp/qkzhang/logs

cat > /root/autodl-tmp/qkzhang/scripts/ollama-watch.sh <<'EOF'
#!/usr/bin/env bash
export PATH=/root/autodl-tmp/qkzhang/ollama/bin:$PATH
export OLLAMA_HOST=127.0.0.1:11434

while true; do
  echo "[$(date '+%F %T')] starting ollama serve"
  ollama serve
  echo "[$(date '+%F %T')] ollama stopped, restart in 5s"
  sleep 5
done
EOF

chmod +x /root/autodl-tmp/qkzhang/scripts/ollama-watch.sh

nohup bash /root/autodl-tmp/qkzhang/scripts/ollama-watch.sh \
  > /root/autodl-tmp/qkzhang/logs/ollama-watch.log \
  2>&1 &
```

临时启动：

```bash
nohup ollama serve \
  > /root/autodl-tmp/qkzhang/logs/ollama.log \
  2>&1 &
```

## 4. 检查状态

```bash
ps -ef | grep -E "ollama-watch|ollama serve" | grep -v grep
curl http://127.0.0.1:11434/api/tags
ollama list
nvidia-smi
```

如果 `qwen3:8b` 不存在：

```bash
ollama pull qwen3:8b
```

## 5. 关闭 Ollama

只释放模型显存，保留 Ollama 服务：

```bash
ollama stop qwen3:8b
nvidia-smi
```

完全关闭服务：

```bash
pkill -f "/root/autodl-tmp/qkzhang/scripts/ollama-watch.sh"
pkill -f "ollama serve"
```

确认关闭：

```bash
ps -ef | grep -E "ollama-watch|ollama serve" | grep -v grep
curl http://127.0.0.1:11434/api/tags
nvidia-smi
```

如果 `curl` 连接失败，说明服务已关闭。

## 6. Windows 开发时启动 SSH 隧道

后端在 Windows 时，需要另开 PowerShell：

```powershell
while ($true) {
  ssh -NT `
    -i "$env:USERPROFILE\.ssh\autodl_ollama" `
    -p 2338 `
    -o ServerAliveInterval=30 `
    -o ServerAliveCountMax=3 `
    -o ExitOnForwardFailure=yes `
    -L 11434:127.0.0.1:11434 `
    root@gpu.chzmark.com

  Start-Sleep -Seconds 5
}
```

Windows 检查：

```powershell
curl.exe http://127.0.0.1:11434/api/tags
```

关闭隧道：在该 PowerShell 窗口按 `Ctrl+C`。

## 7. 后端配置

当前默认配置：

```yaml
vasp.assistant.enabled: true
vasp.assistant.base-url: http://127.0.0.1:11434
vasp.assistant.model: qwen3:8b
vasp.assistant.keep-alive: 30m
```

环境变量覆盖：

```bash
export OLLAMA_BASE_URL=http://127.0.0.1:11434
export OLLAMA_MODEL=qwen3:8b
export OLLAMA_KEEP_ALIVE=30m
```

## 8. 为什么模型能回答当前数据集问题

当前不是微调模型。

系统没有重新训练 `qwen3:8b` 的参数，也没有把数据集写进模型权重。当前方式是“后端受控上下文注入”：

1. 前端携带 `datasetId` 或 `recordId`。
2. 后端从 H2 展示库读取数据集目录、数据集详情或当前记录字段。
3. 后端把真实字段拼成结构化上下文。
4. 后端调用 Ollama `/api/chat`，把上下文和用户问题一起发给 `qwen3:8b`。
5. 系统提示要求模型优先依据平台上下文回答；没有提供的字段必须说明“当前平台上下文未提供”。

所以它能回答当前数据集问题，是因为每次提问时后端把相关数据提供给了模型，不是因为模型被微调了。

## 9. 能否问无关问题

技术上可以问。

`qwen3:8b` 是通用大模型，登录用户问无关问题时，它可能会根据自身通用知识回答。但当前系统提示把它定位为 VASP Show 科研助手，要求优先围绕平台数据回答。

当前限制：

- 游客不能使用智能助手。
- 登录用户可以提问。
- 尚未做严格的话题拦截。
- 尚未接入论文全文检索、引用片段和问答审计。

如果后续要严格限制无关问题，可以增加后端话题分类、关键词/意图拦截、问答日志和限流。

## 10. 常见排查

Windows 后端报无法连接模型：

```powershell
curl.exe http://127.0.0.1:11434/api/tags
```

Linux 上检查：

```bash
curl http://127.0.0.1:11434/api/tags
```

GPU 显存没有释放：

```bash
ollama stop qwen3:8b
pkill -f "/root/autodl-tmp/qkzhang/scripts/ollama-watch.sh"
pkill -f "ollama serve"
nvidia-smi
```
