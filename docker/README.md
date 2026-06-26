# Docker Deployment

在项目根目录执行：

```bash
docker compose -f docker/docker-compose.yml up --build -d
```

默认访问地址：

```text
http://localhost:8088
```

如需改端口：

```bash
FRONTEND_PORT=80 docker compose -f docker/docker-compose.yml up --build -d
```

停止服务：

```bash
docker compose -f docker/docker-compose.yml down
```
