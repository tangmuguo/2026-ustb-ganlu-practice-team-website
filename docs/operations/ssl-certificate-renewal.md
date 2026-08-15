# ganlu.site HTTPS 证书续期操作

适用服务器：腾讯云 Lighthouse Ubuntu 22.04；Nginx 证书位置为：

```text
/etc/nginx/ssl/ganlu.site/fullchain.crt
/etc/nginx/ssl/ganlu.site/privkey.key
```

脚本 `deploy/nginx/renew-ganlu-tls.sh` 只会替换上述两个证书文件、校验并重载 Nginx。它**不会**修改数据库、应用服务、前端文件、环境变量、数据库密码或 JWT 密钥。

## 首次保存到服务器

在服务器终端执行一次：

```bash
sudo install -o root -g root -m 0750 \
  /home/ubuntu/ganlu/deploy/nginx/renew-ganlu-tls.sh \
  /usr/local/sbin/renew-ganlu-tls

sudo /usr/local/sbin/renew-ganlu-tls --help
```

如果服务器的项目目录尚未包含该脚本，先通过文件管理器把本仓库的 `deploy/nginx/renew-ganlu-tls.sh` 上传到 `/home/ubuntu/ssl-upload/renew-ganlu-tls.sh`，再执行：

```bash
sudo install -o root -g root -m 0750 \
  /home/ubuntu/ssl-upload/renew-ganlu-tls.sh \
  /usr/local/sbin/renew-ganlu-tls
```

## 每次续期

1. 在腾讯云 SSL 证书控制台对 `ganlu.site` 申请/续期证书，等待状态显示“已签发”。域名仍由腾讯云 DNS 托管时，选择自动 DNS 验证即可。
2. 下载 **Nginx** 格式证书包；将新文件上传到服务器目录 `/home/ubuntu/ssl-upload/`，文件名保持为：

   ```text
   ganlu.site_bundle.crt
   ganlu.site.key
   ```

3. 在服务器执行一条命令：

   ```bash
   sudo /usr/local/sbin/renew-ganlu-tls
   ```

脚本会先确认域名、证书/私钥匹配与有效期，然后备份当前证书到：

```text
/etc/nginx/ssl/ganlu.site/backups/时间戳/
```

随后执行 `nginx -t`，通过后仅重载 Nginx。期望看到 `Nginx 状态：active` 以及新的到期时间。

若下载后的文件名不同，不必改名，可直接传入两个路径：

```bash
sudo /usr/local/sbin/renew-ganlu-tls \
  /home/ubuntu/ssl-upload/新证书_bundle.crt \
  /home/ubuntu/ssl-upload/新证书.key
```

## 续期后的检查

在浏览器确认以下地址：

```text
https://ganlu.site
http://ganlu.site   （应跳转到 HTTPS）
```

确认无误后，使用文件管理器删除 `/home/ubuntu/ssl-upload/` 中已不需要的证书上传副本，特别是 `.key` 文件；Nginx 使用的正式私钥仍安全保存在 `/etc/nginx/ssl/ganlu.site/privkey.key`。

建议在证书到期前 30 天和 15 天各设置一次提醒。当前证书的到期日可在服务器上随时查看：

```bash
sudo openssl x509 -in /etc/nginx/ssl/ganlu.site/fullchain.crt -noout -dates
```
