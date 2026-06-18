# Langfuse Prompt 管理说明

## 保留变量

运行时固定保留以下变量，管理端创建或更新 Prompt 时必须保留，不能改名或删除：

- `{{userQuery}}`
- `{{knowledgeContext}}`

当前运行时还会继续提供以下可选变量，供 Langfuse Prompt 自由使用：

- `{{attachmentSummary}}`
- `{{nativeAttachmentHint}}`
- `{{fallbackAttachmentText}}`
- `{{ragChunkCount}}`

## 版本语义

- Langfuse Prompt 内容是不可变的。
- 当对同名 Prompt 再次执行 `POST /api/ai/prompts` 时，Langfuse 会创建一个新版本，而不是覆盖旧版本。
- `type` 在首次创建后不可变；若 Langfuse 中同名 Prompt 已存在，后续新版本必须沿用原类型。

## 发布与回滚

- 运行时默认通过 `production` 标签拉取 Prompt。
- 新版本创建后会由 Langfuse 自动维护 `latest` 标签。
- 发布流程：
  1. `POST /api/ai/prompts` 创建新版本。
  2. `PUT /api/ai/prompts/{name}/labels` 把目标版本切到 `production`。
- 回滚流程：
  1. 查询目标旧版本号。
  2. 再次调用 `PUT /api/ai/prompts/{name}/labels`，把 `production` 指回旧版本。

## 查询与删除

- `GET /api/ai/prompts/{name}` 支持通过 `label` 或 `version` 查询，二者互斥。
- `DELETE /api/ai/prompts/{name}` 默认删除该 Prompt 的全部版本。
- `DELETE /api/ai/prompts/{name}?version={version}` 只删除指定版本。
- `DELETE /api/ai/prompts/{name}?label={label}` 删除带指定标签的版本。
- `latest` 是 Langfuse 自动维护标签，管理端不会允许手动设置。

## 缓存说明

- Langfuse Prompt 在运行时存在缓存语义。
- Prompt 发布后，短时间内应用仍可能命中旧版本，这是 Langfuse 官方推荐的可用性与低延迟设计。
- 如果需要立即验证新版本，优先使用：
  - `GET /api/ai/prompts/{name}?label=latest`
  - `GET /api/ai/prompts/{name}?version={version}`
- 生产流量仍建议继续使用 `production` 标签，而不是直接绑定具体版本号。
