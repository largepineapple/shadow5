# 留影 · Shadow Studio

[![CI](https://github.com/largepineapple/shadow5/actions/workflows/verify.yml/badge.svg)](https://github.com/largepineapple/shadow5/actions/workflows/verify.yml)

一个独立运行的素材制作网站：Next.js 负责浏览、上传和异步状态，Java 负责身份、业务与多步骤后台处理，MySQL 持久化任务和历史。制作结果是本地模拟 PNG，无真实 AI 服务。

## 快速启动（推荐容器）

需要 Docker Engine / Docker Desktop（Linux 容器）与 Compose v2。默认端口 3000，数据库和后端不对宿主机暴露。

```sh
cp .env.example .env
docker compose up --build
# 打开 http://localhost:3000
# 停止且保留数据
docker compose down
```

首次启动由 Flyway 创建结构并插入 6 个模板，包含 3 类、1/2/3 张图片要求。封面位于 frontend/public/demo，无外网图片依赖。上传与结果保存在 files 卷，MySQL 保存在 database 卷。构建下载依赖需要网络。示例密码仅用于本地验收，非真实凭据。

## 技术与结构

- Java 21，Spring Boot 3.5.7，MyBatis Starter 3.0.5，Maven 3.9.11，MySQL 8.4；Flyway 版本由 Spring Boot BOM 管理。
- Next.js 16.4.3（App Router）、React / React DOM 19.3.1、TypeScript 5.9.3、TanStack Query 5.90.12；完整精确依赖见 package.json 和 package-lock.json。Node 22，最低需 20.9。
- JUnit / Mockito、Vitest / Testing Library；GitHub Actions 执行两端检查和真实 MySQL 集成测试。

```text
backend/src/main/java/com/shadow/studio/   Java API、身份、文件、任务和工作线程
backend/src/main/resources/db/migration/  数据库迁移及初始化数据
backend/src/test/                         后端单元及 MySQL 集成测试
frontend/app/                            模板列表、模板制作、我的作品
frontend/components/                     身份、上传、任务状态组件
frontend/public/demo/                    原创本地示例封面
docs/开发计划书.md                        需求追踪及开发计划
.github/workflows/verify.yml              push / pull_request 检查
```

## 构建、测试与本地开发

评估者可只使用容器。以下命令同时作为开发入口说明；无需评估者在宿主机安装依赖。

```sh
# 前端（frontend 目录）
npm ci
npm run lint
npm run typecheck
npm test
npm run build
npm run dev
# Java（仓库根目录）
mvn -f backend/pom.xml verify
mvn -f backend/pom.xml spring-boot:run
```

默认本地 Java 连接 localhost:3306/shadow，用户 shadow、密码 local-shadow-password。开发时须自行提供 MySQL 8.4，或映射 Compose 数据库端口。前端默认将 /api 转发至 localhost:8080，Compose 构建时改为 http://api:8080。Next 仅转发，业务和数据库访问全部在 Java。

MySQL 集成测试：设置 MYSQL_INTEGRATION=true、DB_URL、DB_USER、DB_PASSWORD 后执行 Maven verify；未设置时仅该集成测试跳过，单元测试仍运行。CI 已提供 MySQL 并启用该测试。Maven 由 Docker / CI 固定版本环境提供，没有另提交 Wrapper。

配置：DB_URL、DB_USER、DB_PASSWORD、STORAGE_DIR（默认 ./.data）、APP_ORIGIN（默认 http://localhost:3000）、DEMO_MODE（默认 true）、SECURE_COOKIE（HTTPS 应启用）。前端 API_ORIGIN 为构建时代理目标，NEXT_PUBLIC_DEMO_MODE 为构建时演示控件开关；Compose 将 DEMO_MODE 同时传给两端。更改这些构建配置需重新构建 web。

## API 合约

先 POST /api/session 建立或恢复匿名 Cookie，后续请求带 Cookie。API 错误返回 HTTP 状态码及 message；业务校验 400、身份缺失 401、Origin 不符 403、不存在或非本人资源 404、过大 413、图片类型错误 415。图片数量、真实类型、大小、像素、归属及文件存在均在服务端校验。

| 方法与路径 | 请求与响应 |
| --- | --- |
| POST /api/session | 无 body；返回 {id}，设置 HttpOnly Cookie |
| GET /api/templates?category=旅行记忆 | 返回模板数组：id/title/category/description/coverUrl/requiredImageCount/acceptedMimeTypes/maxFileSizeBytes |
| GET /api/templates/{id} | 返回单个模板，不存在 404 |
| POST /api/assets | multipart file；返回 id/userId/mimeType/sizeBytes/previewUrl |
| GET /api/assets/{id}/content | 所属用户才能访问的图片内容 |
| POST /api/tasks | {templateId,assetIds:[id,...],failAt?,delayMs?}；202 返回已持久化任务，不等待执行 |
| GET /api/tasks?offset=0&limit=30 | 当前用户历史数组，最多 100 条/页 |
| GET /api/tasks/{id} | {task,steps}；每步 stage/status/startedAt/finishedAt |

任务字段：id、userId、templateId、status、stage、failedStage、error、resultUrl、failAt、delayMs、createdAt、updatedAt。数据库时间按 UTC 存储/读取，前端转成本地时间。当前分页采用 offset（上限每页 100），替代计划中的游标，适合本题规模；并发新建时需回首页查看最新任务。

## 后台执行与演示

状态：QUEUED → RUNNING → SUCCEEDED / FAILED。三个阶段固定顺序：ASSET_CHECK（素材检查）→ GENERATE（模拟生成）→ FINALIZE（结果整理）。每阶段单独保存开始/结束，最后阶段生成任务专属 PNG 并保存鉴权预览地址。终态不被查询改写。

单实例 Java 定时工作线程每 500ms 从数据库领取一个等待任务，三个阶段顺序执行。耗时等待不持有数据库事务，HTTP 受理只校验并创建记录，查询只读数据库。多任务排队；关闭页面不会取消 Java 任务。

1. 成功：打开模板，上传所需 JPEG / PNG（每张最多 5MB，最多 2400 万像素），点击开始制作，观察阶段与结果。
2. 失败：详情展开「演示控制」，选择素材检查、模拟生成或结果整理，创建任务。对应阶段稳定失败，后续阶段不执行；历史保存原因。DEMO_MODE=false 时后端拒绝模拟参数、前端隐藏控件。
3. 耗时：每阶段可设 0～30000ms，默认 2500ms，创建时保存快照；三步总耗时约三倍加排队时间。
4. 多任务：连续点击创建多个任务，使用不同模拟设置；任务各自显示，前端查询键包含用户和任务 ID。
5. 刷新恢复：在制作期间打开「我的作品」并刷新，Java 返回个人记录，继续跟踪未完成任务。单个终态停止轮询，无进行中任务时无周期任务查询。
6. 查询异常：浏览器开发工具设离线，任务卡保留已有状态，提示更新不可用；恢复网络后点击「恢复查询」。此错误不会改成业务 FAILED。
7. 结果：成功图片点击打开完整预览；模拟图为 Java2D 原创示意，未实际拼合上传图，符合模拟生成范围。

## 身份与安全边界

首次访问创建随机匿名会话，HttpOnly、SameSite=Lax Cookie 有效期一年；数据库保存 SHA-256 令牌摘要。刷新保持个人记录，清除 Cookie 或使用无痕窗口代表另一个用户。新用户看不到旧用户任务，不能提交或预览旧用户素材。演示隔离时用普通窗口与无痕窗口，不需要登录或真实账号。

无账号找回、跨设备同步或页面内用户切换；匿名身份丢失后无法恢复原记录。上传用随机 UUID 存储名，实际解码校验内容，不信任文件名/MIME；资源访问先校验数据库归属。SQL 使用 MyBatis 绑定参数。写请求检查浏览器 Origin，不接受跨站 Origin。

## 已完成、边界与验证

源码包含模板接口、上传校验、匿名隔离、三阶段队列、失败控制、历史和预览、前端状态管理、CI 与 Compose。准确运行结果见 docs/验证记录.md；未实际运行的环境检查不会列为通过。

取舍：单实例、无消息队列、无自动重试/受理幂等/断点续跑；重启将遗留 RUNNING 标成失败并保存中断原因，QUEUED 保留等待领取。未做生产集群和严格 exactly-once。未引用计费/会员/真实 AI。未实现未引用上传与失败孤立文件的自动清理，演示结束后可人工备份再维护清理。任务执行中存储/数据库持续故障可能需要人工介入。前端刷新未提交的上传位会清空，已创建任务和历史不会丢失。没有承诺生产级审计和速率限制。

本地 Docker 引擎不可用，但 GitHub Actions Linux 环境已通过完整 Compose 构建、启动、上传与四任务冒烟，详见验证记录。已读取题目列表和详情参考图，保留分类卡片与左侧制作/右侧历史的主要布局；品牌、素材和配色采用原创方案。

## 部署与回滚

1. 备份 MySQL 和 files 卷，记录当前 commit 与镜像标签。
2. 检出验收 commit，配置 .env，执行 docker compose up --build -d。
3. 检查 docker compose ps / logs，打开站点完成上传、成功和失败冒烟。
4. 回滚检出上一 commit，重新构建启动；保持兼容数据库迁移。不兼容结构需维护停机并恢复配套备份。docker compose down 不删卷；不要以 down -v 作为回滚。

## AI 使用、来源与实际投入

本项目使用 Codex 辅助完成需求拆解、API/状态机设计、源码生成和修改、测试与检查；未复制外部项目脚手架。开源依赖按各自许可证使用。本地封面和 Java2D 结果为项目原创，见 docs/素材来源.md。

AI 工作流示例：将 R-07/R-08 转成独立任务查询与“请求错误/业务失败”两类状态；生成组件后执行 Testing Library 测试，发现模拟路径使用 /a 导致匹配所有 /api 请求，修正成精确后缀并重新验证；没有用修改业务状态来让测试通过。所有验证以实际命令输出为准，不以生成代码作为完成证据。

实际投入按执行会话记录在 docs/验证记录.md；计划估算 27～35 小时不是实际投入。验收以最终提交 SHA 为准，执行 git rev-parse HEAD 获取；源码仓库：https://github.com/largepineapple/shadow5。


容器冒烟：启动后运行 python scripts/smoke.py（标准库，无额外依赖）。上传示例位于 frontend/public/demo/sample.png；也可选自己的照片。CI 的 compose 作业执行同样的容器冒烟。

