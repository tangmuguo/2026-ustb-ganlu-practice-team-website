# 甘露支教前端

本目录是甘露支教网站的 Vue 3 单页应用，提供公开页面、团队风采、新闻、课件共享、互动留言，以及按管理员、团队和学生角色显示的管理入口。

后端位于仓库根目录的 `backend/`，本地默认地址为 `http://localhost:8080/`。

## 环境要求

- Node.js 22.12 或更高版本，或 Node.js 24 LTS；
- 已启动并完成数据库迁移的后端服务；
- Windows PowerShell 下建议使用 `npm.cmd`，避免本机执行策略拦截 `npm.ps1`。

## 启动与构建

首次安装依赖并启动开发服务器：

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

浏览器访问 `http://localhost:5173/`。开发环境使用 `.env.development` 中的 `VITE_API_BASE_URL`，默认指向本地后端。

生成生产构建并执行前端契约测试：

```powershell
npm.cmd run build
node --test tests/*.test.js
```

构建产物位于 `dist/`。部署时，生产环境的 `.env.production` 默认通过 `/api/` 调用反向代理后的后端，而不是直接暴露 `8080` 端口。

## 环境变量

可提交的示例配置见 `.env.example`。前端只应保存可公开的构建配置，例如 API 基址；不要在任何 `.env*` 文件中写入数据库密码、JWT 密钥、扫描服务凭据或个人信息。

| 文件 | 用途 | 默认 API 地址 |
| --- | --- | --- |
| `.env.development` | 本地 Vite 开发 | `http://localhost:8080/` |
| `.env.production` | 生产构建 | `/api/` |
| `.env.example` | 新环境参考 | 本地开发地址 |

## 主要目录

```text
src/apis/          后端 API 封装
src/components/    通用组件与业务组件
src/config/        公开文字、联系方式等集中配置
src/layouts/       默认页和后台页布局
src/router/        路由、登录与角色前置校验
src/stores/        Pinia 状态
src/views/         页面级组件
tests/             Node 内置测试运行器的前端契约测试
```

## 功能与权限边界

- 游客可访问首页、关于甘露、团队风采、新闻、课件共享和经审核的互动留言；
- 管理员管理轮播图、新闻、团队/学生账号、内容审核、举报、团队内容和隐私权利工单；
- 团队账号只能管理本团队学生、团队内容和本人课件；
- 学生完成核验及监护人授权前，不能发表互动内容；已登录角色均可创建自己的隐私权利工单；
- 前端菜单和路由守卫只改善使用体验，后端仍会对每个受保护接口执行令牌和角色校验。

文件上传相关页面依赖后端的隔离和扫描门禁。扫描服务未配置、超时或失败时，文件会停留在待处理状态，前端不应尝试绕过该限制。

## 维护约定

“关于甘露”、联系方式和备案号统一维护在 `src/config/siteContent.js`。修改公开信息前确认其准确性与公开授权，并在提交前运行上述构建和测试命令。
