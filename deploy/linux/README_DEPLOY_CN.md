# VASP Show Linux 部署说明

此包用于 Linux 服务器部署展示站点。默认部署形态：

- 后端 Spring Boot：`127.0.0.1:8080`
- 前端静态文件：由 Nginx 提供
- 公开访问：Nginx 监听 `80`，将 `/api/` 反代到后端
- 展示数据库：`documents/data/frontend_template_data.mv.db`

## 1. 服务器要求

- JDK 17
- Nginx
- 可选：systemd，用于后台常驻后端服务

Ubuntu/Debian 示例：

```bash
sudo apt update
sudo apt install -y openjdk-17-jre nginx unzip
```

## 2. 解压

```bash
mkdir -p /opt/vasp-show
unzip vasp-show-linux-package.zip -d /opt/vasp-show
cd /opt/vasp-show
```

## 3. 启动后端

临时启动：

```bash
bash deploy/linux/start-backend.sh
```

检查：

```bash
curl http://127.0.0.1:8080/api/health
```

停止：

```bash
bash deploy/linux/stop-backend.sh
```

## 4. 配置 Nginx

将示例配置复制到 Nginx：

```bash
sudo cp deploy/linux/nginx-vasp-show.conf /etc/nginx/conf.d/vasp-show.conf
sudo nginx -t
sudo systemctl reload nginx
```

然后访问：

```text
http://服务器公网IP/
```

如果有域名，把 `nginx-vasp-show.conf` 里的 `server_name _;` 改成域名。

## 5. 后台常驻，可选

```bash
sudo cp deploy/linux/vasp-show-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now vasp-show-backend
sudo systemctl status vasp-show-backend
```

## 6. 注意事项

- 包内只包含 H2 展示库，不包含 5GB/6GB 原始 HDF5 大文件。
- 前端使用相对路径 `/api`，推荐同域名由 Nginx 反代，别人拿到服务器链接即可访问。
- 云服务器安全组需要开放 `80` 端口；如用 HTTPS，还需开放 `443`。
