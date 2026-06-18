# 前端 AI 变更说明（Spring AI / Langfuse / 百炼知识库）

本文档基于当前工作区尚未提交的改动整理，面向前端 AI 功能开发与联调。重点覆盖以下几类变化：

- AI 生成链路从旧版 `DeepSeekClient + PromptBuilder` 重构为 `Spring AI + AiGenerationService`
- 新增 Langfuse Prompt / Trace / 评测埋点能力
- RAG 检索支持在本地 ES 与阿里云百炼知识库之间切换
- 旧的动态配置文件与配置接口被移除，配置来源改为 `application.yml + .env`

## 1. 这次改动对前端的核心影响

### 1.1 新增一个前端可直接调用的 AI 预览接口

新增控制器：`/api/ai/generation`

对应代码入口：

- `ai_mail_src/src/main/java/com/github/mail/controller/AiGenerationController.java`

新增接口：

- `POST /api/ai/generation`
- `POST /api/ai/generation/stream`

这两个接口用于前端做“AI 预览生成”与“流式生成预览”。

### 1.2 邮件自动回复与前端预览现在走同一套 AI 生成内核

自动回复调度器不再直接调用旧版 `DeepSeekClient`，而是改成：

1. RAG 检索
2. 构造 `AiGenerationRequest`
3. 调用 `AiGenerationService`
4. 通过 Spring AI 统一完成生成

这意味着：

- 前端预览结果与实际邮件自动回复的生成逻辑更加一致
- 前端联调时，预览接口更能代表真实生产链路

对应代码：

- `ai_mail_src/src/main/java/com/github/mail/service/Schedule/MailAutoReplyScheduler.java`
- `ai_mail_src/src/main/java/com/github/mail/service/ai/SpringAiGenerationService.java`

### 1.3 配置管理方式发生变化

旧版动态配置相关内容已删除：

- 删除 `ai_mail_src/config/config.json`
- 删除 `ai_mail_src/src/main/java/com/github/mail/controller/ConfigController.java`
- 删除旧版 `ConfigService` / `ConfigFileManager`

现在改为：

- `application.yml`
- `.env`
- `@ConfigurationProperties`

这对前端的直接影响是：

- 前端**不能再依赖 `/api/config` 读写 AI 配置**
- Provider、Langfuse、百炼知识库、邮件调度等配置都转为后端部署时注入
- 如果前端之前有“系统配置页”，需要重新评估哪些项还能在线编辑，哪些要改成只读展示或移除

## 2. 当前工作区改动摘要

当前未提交改动（按 `git diff --stat`）：

- 40 个文件改动
- 约 207 行新增
- 约 1828 行删除

从架构角度看，主要是一次“旧 AI 调用方式 -> Spring AI 统一抽象”的重构，不是简单增量开发。

### 2.1 重要新增/引入

- Spring AI 依赖
- Langfuse Java SDK
- 百炼 `bailian20231229` SDK
- AI 生成控制器
- AI Provider 注册中心
- Langfuse Prompt/Trace/Evaluation 服务

### 2.2 重要删除/替换

- 旧版 `DeepSeekClient`
- 旧版 `EmbeddingClient` / `AliEmbeddingClient`
- 旧版 `PromptBuilder` / `PromptBuilderImpl`
- 旧版 `ConfigController`
- 旧版 `config.json` 动态配置体系

## 3. 新增前端可用接口

## 3.1 同步生成预览

### 接口

`POST /api/ai/generation`

### 请求体

对应 DTO：

- `ai_mail_src/src/main/java/com/github/mail/repo/Ai/dto/AiGenerationPreviewRequest.java`

字段说明：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `providerId` | string | 否 | 指定 AI Provider；为空时使用后端默认 Provider |
| `userQuery` | string | 是 | 用户输入内容/邮件正文 |
| `useRag` | boolean | 否 | 是否启用知识库检索，默认 `true` |
| `topK` | number | 否 | 本次预览覆盖 RAG 返回条数 |
| `minScore` | number | 否 | 本次预览覆盖 RAG 最低分数阈值 |

### 请求示例

```json
{
  "providerId": "default",
  "userQuery": "请帮我生成一封回复邮件，说明发票会在3个工作日内补发。",
  "useRag": true,
  "topK": 5,
  "minScore": 0.25
}
```

### 响应体

对应 DTO：

- `ai_mail_src/src/main/java/com/github/mail/service/ai/AiGenerationResult.java`

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| `content` | string | 生成的最终回复内容 |
| `providerId` | string | 实际使用的 Provider ID |
| `model` | string | 实际使用的聊天模型名 |
| `promptMetadata` | object | Prompt 来源信息 |
| `inputTokens` | number/null | 输入 token 数 |
| `outputTokens` | number/null | 输出 token 数 |
| `totalTokens` | number/null | 总 token 数 |
| `traceId` | string | Langfuse trace id，可用于排查 |

`promptMetadata` 结构：

| 字段 | 类型 | 说明 |
|---|---|---|
| `source` | string | `langfuse` 或 `fallback` |
| `promptName` | string | Prompt 名称 |
| `promptLabel` | string | Prompt 标签，如 `production` |
| `promptVersion` | number/null | Prompt 版本，可能为空 |

### 响应示例

```json
{
  "content": "您好，发票已为您安排补发，预计将在 3 个工作日内发送，请您留意。",
  "providerId": "default",
  "model": "kimi-k2.5",
  "promptMetadata": {
    "source": "langfuse",
    "promptName": "mail-auto-reply",
    "promptLabel": "production",
    "promptVersion": 3
  },
  "inputTokens": 812,
  "outputTokens": 126,
  "totalTokens": 938,
  "traceId": "8b1fdc9d-0ef9-4c7d-9d37-3f9d8f3f5abc"
}
```

### 前端注意事项

- `providerId` 当前没有“后端查询 provider 列表”的公开接口，前端如需让用户选择模型提供商，需要和后端约定可选值
- `userQuery` 虽然当前未加 Bean Validation，但前端应视为必填
- `topK`、`minScore` 仅影响本次预览请求；不影响系统全局默认值

## 3.2 流式生成预览

### 接口

`POST /api/ai/generation/stream`

### 返回类型

- `Content-Type: text/event-stream`
- 返回 `ServerSentEvent<String>`

### 行为说明

- 每条 SSE 的 `data` 就是一段文本 chunk
- 当前流式接口**只推送文本片段**
- 当前不会单独推送最终 `traceId`、token 统计、完成事件对象

### 对前端的影响

如果前端要做完整流式体验，需要注意：

- 可以直接拼接每个 chunk 到富文本或 textarea
- 如果想展示 token / traceId / prompt 来源，当前流式接口还不够，需要后续扩展 end event 或补一个查询接口

## 4. RAG / 知识库链路变化

## 4.1 RAG 检索现在支持两种后端来源

对应代码：

- `ai_mail_src/src/main/java/com/github/mail/service/KnowledgeBase/RagService.java`
- `ai_mail_src/src/main/java/com/github/mail/service/KnowledgeBase/impl/LocalEsKnowledgeRetrievalProvider.java`
- `ai_mail_src/src/main/java/com/github/mail/client/AliBailianKbClient.java`

当前 `mail.rag.provider` 可选：

- `local`：本地 ES 检索
- `bailian`：阿里云百炼知识库检索

### 这对前端意味着什么

前端预览接口中：

- **不能直接指定 RAG provider**
- 前端只能控制 `useRag / topK / minScore`
- 实际使用 `local` 还是 `bailian`，由后端部署配置 `app.mail.rag.provider` 决定

也就是说：

- 前端发同样的预览请求，在不同环境里可能命中不同知识库实现
- 前端需要把“结果差异”理解为后端配置差异，而不是接口不稳定

## 4.2 百炼知识库接入方式

当前只做了“检索接入”，没有做“文档同步接入”。

已接入：

- 百炼 Retrieve API 检索

未接入：

- AddFile
- SubmitIndexJob
- 文档同步/增量更新

因此前端无需新增百炼文档管理页面，本次只需要知道：

- RAG 结果来源可能是百炼
- 但知识库内容维护仍在后端部署或阿里云控制台侧完成

## 4.3 RAG 参数优先级

预览接口参数优先级如下：

1. 请求体传了 `topK` / `minScore` -> 使用请求体值
2. 未传 -> 使用后端 `app.mail.rag.top-k` / `app.mail.rag.min-score`

对应代码在：

- `ai_mail_src/src/main/java/com/github/mail/controller/AiGenerationController.java`

## 5. Spring AI 重构后，前端应该知道的行为变化

## 5.1 Provider 抽象代替固定 DeepSeek

旧逻辑偏固定：

- DeepSeek 聊天
- 独立 Embedding 客户端

新逻辑改为：

- `SpringAiProviderRegistry` 统一注册 Provider
- 通过兼容 OpenAI 的方式接入聊天模型与 embedding 模型
- `providerId` 可切换不同 provider 运行时

对应代码：

- `ai_mail_src/src/main/java/com/github/mail/service/ai/SpringAiProviderRegistry.java`
- `ai_mail_src/src/main/java/com/github/mail/config/properties/AppAiProperties.java`

### 前端要点

- 现在“模型供应商”的概念已经从单一 DeepSeek 升级为通用 Provider
- 但当前后端**未提供 provider 列表接口**
- 所以前端如果要做 Provider 下拉框，要么写死约定值，要么等后端补列表接口

## 5.2 Prompt 不再是纯本地模板

当前 Prompt 生成逻辑：

1. 优先从 Langfuse 拉 Prompt 模板
2. 拉取失败时，使用本地 fallback Prompt
3. Prompt 变量仍包含：
   - 回复规则
   - 回复策略
   - 知识库上下文
   - 用户输入

对应代码：

- `ai_mail_src/src/main/java/com/github/mail/service/ai/LangfusePromptService.java`

### 前端要点

- 前端不再需要假设 Prompt 一定是固定本地模板
- 可以在 UI 上展示 `promptMetadata.source`：
  - `langfuse`：表示使用远端 Prompt
  - `fallback`：表示 Langfuse 不可用，自动降级

这对调试非常有帮助

## 6. Langfuse 接入对前端的影响

## 6.1 新增 trace 能力

每次生成都会创建 `traceId`，并记录：

- providerId
- chatModel
- embeddingModel
- promptSource
- promptName / label / version
- 是否流式
- token 使用量
- 业务元数据（如 scheduler / preview-api / subject / from 等）

对应代码：

- `ai_mail_src/src/main/java/com/github/mail/service/ai/langfuse/LangfuseTracingService.java`

### 前端建议

前端在“AI 预览面板”中建议显示：

- `providerId`
- `model`
- `promptMetadata.source`
- `traceId`
- `inputTokens / outputTokens / totalTokens`

这样当生成结果不符合预期时，前后端都更容易定位问题。

## 6.2 新增启发式评分

后端会自动向 Langfuse 写入一些启发式分数：

- `generation_success`
- `rag_context_available`
- `mentions_ai_or_kb`

对应代码：

- `ai_mail_src/src/main/java/com/github/mail/service/ai/langfuse/LangfuseEvaluationService.java`

### 前端结论

这些分数当前不会从业务接口返回，因此：

- 前端不用直接消费
- 但调试平台或运营后台未来可以考虑展示

## 7. 现有前端页面的兼容性评估

## 7.1 规则管理页面：继续可用

以下接口仍在：

- `GET /api/ai/reply-rules`
- `POST /api/ai/reply-rules`
- `PUT /api/ai/reply-rules/{id}`
- `DELETE /api/ai/reply-rules/{id}`
- `PUT /api/ai/reply-rules/reorder`
- `GET /api/ai/reply-rules/history`
- `GET /api/ai/reply-strategy`
- `POST /api/ai/reply-strategy`

它们仍然参与 Prompt 变量拼装，所以前端现有规则/策略页面不需要下线。

## 7.2 系统配置页面：需要重点检查

由于以下内容已删除：

- `config.json`
- `ConfigController`

所以前端如果存在“读取/保存 AI 配置”的页面，需要检查是否依赖：

- `GET /api/config`
- `POST /api/config`

如果依赖了，这部分现在应视为**失效**。

建议前端处理方式：

- 临时隐藏配置编辑入口
- 或改成只读说明页
- 或等待后端补新的配置管理 API

## 7.3 AI 预览页面：建议新增

建议前端新增一个“AI 预览/调试”面板，直接调用：

- `POST /api/ai/generation`
- `POST /api/ai/generation/stream`

最小功能建议：

- 输入 `userQuery`
- 勾选 `useRag`
- 可选输入 `topK`
- 可选输入 `minScore`
- 可选选择 `providerId`（如果前后端约定好了）
- 展示最终内容
- 展示 token 统计
- 展示 prompt 来源
- 展示 traceId

## 8. 配置项迁移说明（前端了解即可）

当前主要配置来源为 `application.yml` 和 `.env`。

### 8.1 AI Provider

前缀：

- `app.ai.*`

关键项：

- `app.ai.default-provider`
- `app.ai.default-embedding-provider`
- `app.ai.providers.<providerId>.base-url`
- `app.ai.providers.<providerId>.api-key`
- `app.ai.providers.<providerId>.chat-model`
- `app.ai.providers.<providerId>.embedding-model`

### 8.2 Langfuse

前缀：

- `app.langfuse.*`

关键项：

- `enabled`
- `url`
- `public-key`
- `secret-key`
- `prompt-name`
- `prompt-label`
- `prompt-version`
- `trace-name`
- `environment`

### 8.3 百炼知识库

前缀：

- `app.rag.bailian.*`

关键项：

- `access-key-id`
- `access-key-secret`
- `workspace-id`
- `index-id`
- `endpoint`

### 8.4 邮件自动回复

前缀：

- `app.mail.*`

关键项：

- `app.mail.auto-reply.enabled`
- `app.mail.auto-process.enabled`
- `app.mail.rag.provider`
- `app.mail.rag.top-k`
- `app.mail.rag.min-score`

## 9. 删除与重构清单（给前端做认知同步）

### 9.1 已删除

- 旧动态配置控制器：`ConfigController`
- 旧配置读写服务：`ConfigService`、`ConfigFileManager`
- 旧 Prompt 组装器：`PromptBuilder`、`PromptBuilderImpl`
- 旧模型客户端：`DeepSeekClient`
- 旧 embedding 抽象：`EmbeddingClient`、`AliEmbeddingClient`
- 旧本地配置模型：`AppConfig`、`DeepSeekConfig`、`EmbeddingConfig`、`BailianConfig`

### 9.2 已重构/替换

- 邮件自动回复生成：改为 `AiGenerationService`
- Embedding：改为 `AiEmbeddingService`
- Provider 管理：改为 `SpringAiProviderRegistry`
- Prompt 管理：改为 `LangfusePromptService`
- RAG 路由：改为 `RagService + KnowledgeRetrievalProvider`

## 10. 前端需要立即执行的事项

1. 新增或改造 AI 预览页面，接入 `/api/ai/generation`
2. 如果需要流式体验，接入 `/api/ai/generation/stream`
3. 在预览结果中展示 `traceId`、token、prompt 来源
4. 检查现有前端是否依赖 `/api/config`，如有则尽快下线或改造
5. 保留现有“AI 回复规则 / 回复策略”页面，不需要删除
6. 不要在前端假设 RAG 一定来自本地 ES；结果来源可能是百炼
7. 如果要做 provider 切换 UI，先和后端约定 `providerId` 列表，因为当前没有 provider 列表接口

## 11. 建议的联调顺序

1. 先用 `POST /api/ai/generation` 做同步联调
2. 确认返回 `content / providerId / model / traceId / token`
3. 再验证 `useRag=false` 与 `useRag=true` 的效果差异
4. 再验证不同环境下 `mail.rag.provider=local/bailian` 的行为差异
5. 最后再接入流式接口

## 12. 结论

本次未提交改动本质上是一次 AI 后端能力升级：

- 从“单模型直连”升级到“Spring AI Provider 抽象”
- 从“本地固定 Prompt”升级到“Langfuse Prompt + Trace”
- 从“单一本地知识库”升级到“本地 ES / 百炼知识库可切换”
- 从“前端可改 config.json”升级到“后端部署配置驱动”

前端最需要关注的不是内部类名变化，而是这四件事：

- 有了新的 AI 预览接口
- 返回结果多了可观测元数据
- 配置接口没了
- RAG 来源不再固定
