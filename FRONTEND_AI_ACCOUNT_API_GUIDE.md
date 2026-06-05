# 前端 AI 对接指南（账户/鉴权/管理端）

本文档用于让前端 AI 了解后端本次更新新增的账户相关接口与鉴权行为，以便同步更新前端代码与交互流程。

## 1. 基本信息

- 服务默认端口：`8080`
- API 前缀：`/api`
- 请求/响应：JSON 为主（UTF-8）

## 2. 鉴权规则（非常重要）

### 2.1 Bearer Token

除“登录/注册”等放行接口外，其余接口默认被 JWT 拦截器保护，请求必须携带：

```
Authorization: Bearer <token>
```

### 2.2 放行（无需 token）的接口

- `POST /api/account/login`
- `POST /api/account/register`
- `POST /api/home/login`（旧接口兼容保留）

### 2.3 拦截器行为与前端注意事项

- 后端会在每次请求时根据 token 的 `subject`（用户名）查库确认用户是否存在且未被禁用：
  - 用户状态为 `DISABLED` 时，任何受保护接口将返回 `401`
- 后端对管理端权限判断以数据库 `role` 为准（而不是 token 内 claim），前端不要仅依赖 token 解码结果来决定权限；UI 上可以做“预判显示”，但最终以接口返回为准。

### 2.4 登出（服务端失效）开关

- 后端支持可选的“服务端登出 token 失效表”机制
- 若 `security.account.server-logout-enabled=false`（默认），`/api/account/logout` 仅返回成功但不会让 token 在服务端立即失效；前端仍应清除本地 token（等 token 过期即可）

## 3. 用户模型关键字段（用于前端展示/筛选）

后端返回的用户信息（`UserInfoDTO`）字段：

| 字段 | 类型 | 含义 |
|---|---|---|
| id | number | 用户主键 |
| username | string | 用户名（对应 DB 的 `user`） |
| role | string | `ADMIN` / `USER` |
| status | string | `ACTIVE` / `DISABLED` |
| createdAt | string | 创建时间（`yyyy-MM-dd HH:mm:ss` 或 ISO，取决于 Jackson 配置） |
| updatedAt | string | 更新时间 |

枚举值：
- `role`: `ADMIN`, `USER`
- `status`: `ACTIVE`, `DISABLED`

## 4. 账户自助接口（/api/account）

### 4.1 注册

**POST** `/api/account/register`

请求体：
```json
{
  "username": "admin",
  "password": "Passw0rd123"
}
```

成功响应（HTTP 200）：
```json
{
  "success": true,
  "message": "注册成功",
  "user": {
    "id": 1,
    "username": "admin",
    "role": "ADMIN",
    "status": "ACTIVE",
    "createdAt": "2026-02-04 10:00:00",
    "updatedAt": "2026-02-04 10:00:00"
  }
}
```

失败响应（HTTP 400）示例：
```json
{ "success": false, "message": "不允许自助注册" }
```

注册策略：
- 当系统中 **不存在任何用户** 时：允许注册，且首个用户会被设置为 `ADMIN`
- 当系统中 **已有用户** 时：默认拒绝注册（可通过后端配置打开）

前端建议：
- 注册入口建议仅用于“初始化环境/首个管理员”，或做“尝试注册→失败则隐藏入口”的弱引导
- 密码长度校验：至少 8 位（可配置，默认 8）

### 4.2 登录（新接口）

**POST** `/api/account/login`

请求体：
```json
{
  "username": "admin",
  "password": "Passw0rd123"
}
```

成功响应（HTTP 200）：
```json
{
  "success": true,
  "message": "登陆成功",
  "token": "<jwt>"
}
```

失败响应（HTTP 401）示例：
```json
{ "success": false, "message": "用户名或密码错误" }
```

前端建议：
- 登录成功后存储 token（localStorage / cookie / memory 均可，按前端安全策略选择）
- 所有受保护请求统一注入 `Authorization` 头

### 4.3 登录（旧接口兼容）

**POST** `/api/home/login`

该接口仍存在，供旧前端兼容。其请求体通常是：
```json
{
  "user": "admin",
  "passwd": "Passw0rd123"
}
```

返回值可能是 token 字符串或错误字符串（历史兼容行为）。若你在做前端升级，建议迁移到 `/api/account/login`。

### 4.4 当前用户信息

**GET** `/api/account/me`

请求头：
```
Authorization: Bearer <jwt>
```

成功响应（HTTP 200）：
```json
{
  "success": true,
  "user": {
    "id": 1,
    "username": "admin",
    "role": "ADMIN",
    "status": "ACTIVE",
    "createdAt": "2026-02-04 10:00:00",
    "updatedAt": "2026-02-04 10:00:00"
  }
}
```

失败响应（HTTP 401）示例：
```json
{ "success": false, "message": "未登录" }
```

### 4.5 修改密码

**POST** `/api/account/change-password`

请求头：
```
Authorization: Bearer <jwt>
```

请求体：
```json
{
  "oldPassword": "old",
  "newPassword": "newPassw0rd"
}
```

成功响应（HTTP 200）：
```json
{ "success": true, "message": "修改成功" }
```

失败响应（HTTP 400）示例：
```json
{ "success": false, "message": "旧密码错误" }
```

### 4.6 登出

**POST** `/api/account/logout`

请求头：
```
Authorization: Bearer <jwt>
```

成功响应（HTTP 200）：
```json
{ "success": true, "message": "已登出" }
```

前端建议：
- 不论后端是否启用“服务端登出”，前端都应在成功后清除本地 token

## 5. 管理端用户接口（/api/admin/users，仅 ADMIN）

所有接口都需要：
```
Authorization: Bearer <jwt>
```

非管理员响应（HTTP 403）：
```json
{ "success": false, "message": "无权限" }
```

### 5.1 用户列表（分页）

**GET** `/api/admin/users?page=1&size=10`

成功响应（HTTP 200）：
```json
{
  "success": true,
  "total": 100,
  "page": 1,
  "size": 10,
  "records": [
    { "id": 1, "username": "admin", "role": "ADMIN", "status": "ACTIVE", "createdAt": "...", "updatedAt": "..." }
  ]
}
```

分页说明：
- `page` 默认 1（后端直接透传到 MyBatis-Plus 的 Page.current）
- `size` 默认 10

### 5.2 创建用户

**POST** `/api/admin/users`

请求体：
```json
{
  "username": "u2",
  "password": "Passw0rd123",
  "role": "USER"
}
```

成功响应（HTTP 200）：
```json
{ "success": true, "user": { "id": 2, "username": "u2", "role": "USER", "status": "ACTIVE", "createdAt": "...", "updatedAt": "..." } }
```

### 5.3 查看用户详情

**GET** `/api/admin/users/{id}`

成功响应（HTTP 200）：
```json
{ "success": true, "user": { "id": 2, "username": "u2", "role": "USER", "status": "ACTIVE", "createdAt": "...", "updatedAt": "..." } }
```

失败响应（HTTP 404）示例：
```json
{ "success": false, "message": "用户不存在" }
```

### 5.4 启用/禁用用户

**PATCH** `/api/admin/users/{id}/status`

请求体（可选值：`ACTIVE` / `DISABLED`）：
```json
{ "status": "DISABLED" }
```

成功响应（HTTP 200）：
```json
{ "success": true }
```

### 5.5 重置密码

**POST** `/api/admin/users/{id}/reset-password`

请求体：
```json
{ "password": "NewPassw0rd123" }
```

成功响应（HTTP 200）：
```json
{ "success": true }
```

## 6. 前端改造清单（给前端 AI 的最小执行建议）

1) 新增页面/模块
- 登录页：对接 `/api/account/login`
- 注册页（可选）：对接 `/api/account/register`，并处理“系统已初始化时不允许注册”的失败提示
- 个人中心：展示 `/api/account/me`、提供改密与登出
- 管理端用户管理：列表/详情/创建/禁用启用/重置密码

2) 统一请求拦截（建议实现）
- 请求前：若有 token 则加 `Authorization: Bearer <token>`
- 响应后：遇到 `401` 时清空 token 并跳转登录页
- 遇到 `403` 时提示“无权限”

3) 权限 UI
- 后端以 DB role 为准：建议以 `/api/account/me` 返回的 `role` 决定是否展示“管理端入口”

## 7. 后端可配置项（前端只需理解行为）

位于 `application.yml`：
- `security.account.allow-self-register-after-initialized`：系统已有用户后是否允许自助注册（默认 false）
- `security.account.server-logout-enabled`：是否启用服务端登出失效（默认 false）
- `security.account.min-password-length`：最小密码长度（默认 8）

