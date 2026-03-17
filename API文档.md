# 涉外法律案例查询系统 — 接口文档

**Base URL**：`http://{host}/api`

**数据格式**：所有请求和响应均使用 `application/json`，`null` 字段不返回。

**认证方式**：登录成功后服务端返回 `token`，后续需要认证的请求在请求头中携带：
```
Authorization: Bearer {token}
```

---

## 统一响应结构

所有接口均返回以下结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | Integer | 状态码，200 表示成功 |
| message | String | 提示信息 |
| data | Any | 业务数据，无数据时不返回该字段 |

### 通用错误码

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未登录或 Token 失效 |
| 403 | 无权限（非管理员） |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 1001 | 用户不存在 |
| 1002 | 用户已存在 |
| 1003 | Token 无效或已过期 |
| 1004 | 微信登录失败 |
| 1005 | 账号或密码错误 |
| 1006 | 旧密码错误 |
| 2001 | 案例不存在 |
| 2002 | 案例已存在 |
| 3001 | AI 服务调用失败 |

### 分页响应结构

分页接口的 `data` 字段结构如下：

```json
{
  "records": [],
  "total": 100,
  "size": 10,
  "current": 1,
  "pages": 10
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| records | Array | 当前页数据列表 |
| total | Long | 总记录数 |
| size | Integer | 每页大小 |
| current | Integer | 当前页码 |
| pages | Integer | 总页数 |

---

## 枚举值说明

| 枚举 | 值 | 含义 |
|------|----|------|
| caseType | 1 | 民事 |
| caseType | 2 | 刑事 |
| caseType | 3 | 行政 |
| caseType | 4 | 商事 |
| aiStatus | 0 | 待处理 |
| aiStatus | 1 | 处理中 |
| aiStatus | 2 | 已完成 |
| aiStatus | 3 | 处理失败 |
| role | 0 | 普通用户 |
| role | 1 | 管理员 |
| status | 0 | 正常 |
| status | 1 | 禁用 |

---

## 一、认证模块 `/auth`

### 1.1 微信小程序登录

**权限**：公开

```
POST /auth/wx-login
```

**请求体**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| code | String | ✅ | 微信小程序 `wx.login()` 获取的临时 code |
| nickname | String | ❌ | 用户昵称 |
| avatarUrl | String | ❌ | 用户头像 URL |

**请求示例**：
```json
{
  "code": "081xxx",
  "nickname": "张三",
  "avatarUrl": "https://example.com/avatar.jpg"
}
```

**响应 `data`**：[UserVO](#uservo)

**响应示例**：
```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "nickname": "张三",
    "avatarUrl": "https://example.com/avatar.jpg",
    "role": 0,
    "token": "eyJhbGciOiJIUzI1NiJ9..."
  }
}
```

---

## 二、用户模块 `/user`

### 2.1 获取当前用户信息

**权限**：登录用户

```
GET /user/info
```

**请求参数**：无

**响应 `data`**：[UserVO](#uservo)

---

### 2.2 更新当前用户信息

**权限**：登录用户

```
PUT /user/info
```

**Query 参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| nickname | String | ❌ | 新昵称 |
| avatarUrl | String | ❌ | 新头像 URL |

**响应 `data`**：无

---

### 2.3 管理员账号密码登录

**权限**：公开

```
POST /user/admin/login
```

**请求体**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | ✅ | 管理员账号 |
| password | String | ✅ | 登录密码（明文） |

**请求示例**：
```json
{
  "username": "admin",
  "password": "Admin@123"
}
```

**响应 `data`**：[UserVO](#uservo)（含 `token`）

---

### 2.4 管理员退出登录

**权限**：管理员

```
POST /user/admin/logout
```

> 服务端无状态，前端清除本地 Token 即可，此接口仅作语义保留。

**请求参数**：无

**响应 `data`**：无

---

### 2.5 管理员修改密码

**权限**：管理员

```
PUT /user/admin/password
```

**请求体**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| oldPassword | String | ✅ | 旧密码（明文） |
| newPassword | String | ✅ | 新密码（明文） |

**响应 `data`**：无

---

### 2.6 分页查询用户列表

**权限**：管理员

```
GET /user/admin/list
```

**Query 参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| page | Integer | ❌ | 1 | 页码 |
| pageSize | Integer | ❌ | 20 | 每页大小 |
| keyword | String | ❌ | - | 按昵称或账号模糊搜索 |

**响应 `data`**：分页，列表项为 [UserVO](#uservo)

---

### 2.7 禁用或启用用户

**权限**：管理员

```
PUT /user/admin/{userId}/status
```

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| userId | Long | 目标用户 ID |

**Query 参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | Integer | ✅ | 0-启用，1-禁用 |

**响应 `data`**：无

---

### 2.8 设置或取消管理员角色

**权限**：管理员

```
PUT /user/admin/{userId}/role
```

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| userId | Long | 目标用户 ID |

**请求体**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| role | Integer | ✅ | 0-取消管理员，1-设为管理员 |
| username | String | 条件必填 | role=1 时必填，管理员登录账号 |
| password | String | 条件必填 | role=1 时必填，管理员登录密码（明文） |

**响应 `data`**：无

---

### 2.9 删除用户

**权限**：管理员

```
DELETE /user/admin/{userId}
```

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| userId | Long | 目标用户 ID |

**响应 `data`**：无

---

## 三、案例模块 `/cases`

### 3.1 分页查询案例列表

**权限**：公开（登录后可获取收藏状态；管理员可查看所有状态案例）

```
GET /cases
```

**Query 参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| keyword | String | ❌ | - | 全文搜索关键词（匹配标题、案由、关键词） |
| caseType | Integer | ❌ | - | 案件类型：1-民事 2-刑事 3-行政 4-商事 |
| country | String | ❌ | - | 所属国家/地区 |
| orderBy | String | ❌ | created_at | 排序字段：`importance_score` / `view_count` / `judgment_date` / `created_at` |
| orderDir | String | ❌ | desc | 排序方向：`asc` / `desc` |
| pageNum | Integer | ❌ | 1 | 页码 |
| pageSize | Integer | ❌ | 10 | 每页大小 |

**响应 `data`**：分页，列表项为 [CaseListVO](#caselistvo)

---

### 3.2 获取案例详情

**权限**：公开（登录后自动记录浏览历史，并返回收藏状态）

```
GET /cases/{id}
```

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 案例 ID |

**响应 `data`**：[CaseDetailVO](#casedetailvo)

---

### 3.3 新增案例

**权限**：管理员

```
POST /cases
```

> 保存成功后自动在后台异步触发完整 AI 处理流程（翻译 → 摘要 → 评分）。

**请求体**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| titleEn | String | ✅ | 英文标题 |
| contentEn | String | ✅ | 英文原文 |
| source | String | ✅ | 案例发布来源 |
| url | String | ✅ | 案例原始访问链接 |
| caseNo | String | ❌ | 案例编号 |
| titleZh | String | ❌ | 中文标题（可由 AI 生成） |
| caseType | Integer | ❌ | 案件类型 |
| country | String | ❌ | 所属国家/地区 |
| court | String | ❌ | 审理法院 |
| judgmentDate | String | ❌ | 判决日期，格式 `yyyy-MM-dd` |
| keywords | String | ❌ | 关键词（逗号分隔） |
| legalProvisions | String | ❌ | 涉及法律条文 |

**响应 `data`**：Long — 新建案例的 ID

**响应示例**：
```json
{
  "code": 200,
  "message": "案例保存成功，AI处理已在后台启动",
  "data": 42
}
```

---

### 3.4 修正案例内容

**权限**：管理员

```
PUT /cases/{id}
```

> 仅覆盖请求体中非 `null` 的字段，不会重置 AI 状态，不会重新触发 AI 处理。

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 案例 ID |

**请求体**（所有字段均可选，传 `null` 则不覆盖）：

| 字段 | 类型 | 说明 |
|------|------|------|
| caseNo | String | 案例编号 |
| titleZh | String | 中文标题 |
| titleEn | String | 英文标题 |
| caseReason | String | 案由 |
| caseType | Integer | 案件类型 |
| country | String | 所属国家/地区 |
| court | String | 审理法院 |
| judgmentDate | String | 判决日期，格式 `yyyy-MM-dd` |
| contentEn | String | 英文原文 |
| contentZh | String | 中文翻译 |
| disputeFocus | String | 争议焦点 |
| judgmentResult | String | 判决结果 |
| summaryZh | String | 核心摘要 |
| importanceScore | Integer | 重要性评分（0-100） |
| scoreReason | String | 评分理由 |
| keywords | String | 关键词（逗号分隔） |
| legalProvisions | String | 涉及法律条文 |
| source | String | 案例发布来源 |
| url | String | 案例原始访问链接 |

**响应 `data`**：无

---

### 3.5 逻辑删除案例

**权限**：管理员

```
DELETE /cases/{id}
```

> 将案例标记为已删除，普通用户不可见，可通过恢复接口撤销。

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 案例 ID |

**响应 `data`**：无

---

### 3.6 恢复已删除案例

**权限**：管理员

```
PUT /cases/{id}/restore
```

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 案例 ID |

**响应 `data`**：无

---

### 3.7 物理删除案例

**权限**：管理员

```
DELETE /cases/{id}/permanent
```

> ⚠️ 从数据库彻底移除，操作不可逆，请谨慎使用。

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 案例 ID |

**响应 `data`**：无

---

## 四、AI 处理模块（管理员）

> 以下接口均为**异步执行**，调用后立即返回，AI 任务在后台运行。可通过对应的记录查询接口轮询处理进度。

### 4.1 手动触发翻译

**权限**：管理员

```
POST /cases/{id}/translate
```

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 案例 ID |

**响应示例**：
```json
{
  "code": 200,
  "message": "翻译任务已提交，正在后台处理"
}
```

---

### 4.2 手动触发摘要提取

**权限**：管理员

```
POST /cases/{id}/summary
```

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 案例 ID |

**响应示例**：
```json
{
  "code": 200,
  "message": "摘要提取任务已提交，正在后台处理"
}
```

---

### 4.3 手动触发重要性评分

**权限**：管理员

```
POST /cases/{id}/score
```

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 案例 ID |

**响应示例**：
```json
{
  "code": 200,
  "message": "评分任务已提交，正在后台处理"
}
```

---

## 五、AI 处理记录查询（管理员）

### 5.1 查询翻译记录

**权限**：管理员

```
GET /cases/{id}/translations
```

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 案例 ID |

**响应 `data`**：Array\<[CaseTranslationRecordVO](#casetranslationrecordvo)\>，按创建时间倒序

---

### 5.2 查询摘要记录

**权限**：管理员

```
GET /cases/{id}/summaries
```

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 案例 ID |

**响应 `data`**：Array\<[CaseSummaryRecordVO](#casesummaryrecordvo)\>，按创建时间倒序

---

### 5.3 查询评分记录

**权限**：管理员

```
GET /cases/{id}/scores
```

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 案例 ID |

**响应 `data`**：Array\<[CaseScoreRecordVO](#casescorerecordvo)\>，按创建时间倒序

---

## 六、收藏模块（登录用户）

### 6.1 收藏 / 取消收藏案例

**权限**：登录用户

```
POST /cases/{id}/favorite
```

> 切换逻辑：已收藏则取消，未收藏则添加。

**Path 参数**：

| 参数 | 类型 | 说明 |
|------|------|------|
| id | Long | 案例 ID |

**响应 `data`**：Boolean — `true` 表示当前已收藏，`false` 表示已取消收藏

**响应示例**：
```json
{
  "code": 200,
  "message": "收藏成功",
  "data": true
}
```

---

### 6.2 获取我的收藏列表

**权限**：登录用户

```
GET /cases/favorites
```

**Query 参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | ❌ | 1 | 页码 |
| pageSize | Integer | ❌ | 10 | 每页大小 |

**响应 `data`**：分页，列表项为 [CaseListVO](#caselistvo)

---

## 七、浏览记录模块（登录用户）

> 每次调用「获取案例详情」接口时，系统自动记录/更新当前用户的浏览记录。

### 7.1 获取我的浏览记录

**权限**：登录用户

```
GET /cases/browse-history
```

**Query 参数**：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | ❌ | 1 | 页码 |
| pageSize | Integer | ❌ | 10 | 每页大小 |

**响应 `data`**：分页，列表项为 [BrowseHistoryVO](#browsehistoryvo)

---

### 7.2 删除浏览记录（支持批量）

**权限**：登录用户

```
DELETE /cases/browse-history
```

> 只能删除当前登录用户自己的记录。

**请求体**：Long 数组，浏览记录 ID 列表

**请求示例**：
```json
[1, 2, 3]
```

**响应 `data`**：无

---

## 八、Agent 直接调用模块 `/agent`

> 用于测试或前端直接调用 AI 能力，不关联任何案例记录。

### 8.1 文本翻译（英→中）

**权限**：公开

```
POST /agent/translate
```

**请求体**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | String | ✅ | 待翻译的英文文本 |

**响应 `data`**：String — 翻译后的中文文本

---

### 8.2 摘要提取

**权限**：公开

```
POST /agent/summary
```

**请求体**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | String | ✅ | 待提取摘要的文本内容 |

**响应 `data`**：[CaseSummaryVO](#casesummaryvo)

---

### 8.3 重要性评分

**权限**：公开

```
POST /agent/score
```

**请求体**：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | String | ✅ | 待评分的文本内容 |

**响应 `data`**：[CaseScoreVO](#casescorevo)

---

## 九、爬虫管理模块 `/admin/crawler`

**权限**：所有接口均为管理员专用

### 9.1 启动全量采集（CourtListener）

```
POST /admin/crawler/start
```

> 异步执行，遍历所有预设关键词进行采集，立即返回。

**响应示例**：
```json
{
  "code": 200,
  "message": "全量采集任务已启动（异步执行中）"
}
```

---

### 9.2 按关键词采集（CourtListener）

```
POST /admin/crawler/query
```

> 同步执行，适合测试或补采单个关键词。

**Query 参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | String | ✅ | 采集关键词，如 `Huawei` |

**响应 `data`**：

| 字段 | 类型 | 说明 |
|------|------|------|
| keyword | String | 本次采集的关键词 |
| savedCount | Integer | 新入库案例数量 |

---

### 9.3 查询采集任务状态（CourtListener）

```
GET /admin/crawler/status
```

**响应 `data`**：

| 字段 | 类型 | 说明 |
|------|------|------|
| running | Boolean | 是否正在运行 |
| message | String | 状态描述 |

---

### 9.4 启动 Python 爬虫

```
POST /admin/crawler/python/start
```

**Query 参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| crawler | String | ✅ | 爬虫名称，对应脚本文件 `{name}_crawler.py` |

---

### 9.5 停止 Python 爬虫

```
POST /admin/crawler/python/stop
```

**Query 参数**：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| crawler | String | ✅ | 爬虫名称 |

---

### 9.6 查询所有 Python 爬虫状态

```
GET /admin/crawler/python/status
```

**响应 `data`**：Map\<String, Boolean\> — key 为爬虫名称，value 为是否正在运行

**响应示例**：
```json
{
  "code": 200,
  "data": {
    "site_a": true,
    "site_b": false
  }
}
```

---

## 数据结构说明

### UserVO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 用户 ID |
| nickname | String | 昵称 |
| avatarUrl | String | 头像 URL |
| phone | String | 手机号 |
| username | String | 管理员登录账号 |
| role | Integer | 角色：0-普通用户，1-管理员 |
| token | String | JWT Token（仅登录接口返回） |
| status | Integer | 状态：0-正常，1-禁用（管理员视图） |
| createdAt | String | 注册时间（管理员视图），格式 `yyyy-MM-ddTHH:mm:ss` |

---

### CaseListVO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 案例 ID |
| caseNo | String | 案例编号 |
| titleZh | String | 中文标题 |
| titleEn | String | 英文标题 |
| caseReason | String | 案由 |
| caseType | Integer | 案件类型（见枚举） |
| country | String | 所属国家/地区 |
| court | String | 审理法院 |
| judgmentDate | String | 判决日期，格式 `yyyy-MM-dd` |
| summaryZh | String | 核心摘要 |
| importanceScore | Integer | 重要性评分（0-100） |
| keywords | String | 关键词（逗号分隔） |
| source | String | 案例发布来源 |
| url | String | 案例原始链接 |
| aiStatus | Integer | AI 处理状态（见枚举） |
| viewCount | Integer | 查看次数 |
| favoriteCount | Integer | 收藏次数 |
| createdAt | String | 创建时间，格式 `yyyy-MM-ddTHH:mm:ss` |
| isFavorited | Boolean | 当前用户是否已收藏（未登录时为 `false`） |

---

### CaseDetailVO

在 [CaseListVO](#caselistvo) 基础上增加以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| contentEn | String | 英文原文 |
| contentZh | String | 中文翻译 |
| disputeFocus | String | 争议焦点 |
| judgmentResult | String | 判决结果 |
| scoreReason | String | 评分理由 |
| legalProvisions | String | 涉及法律条文 |
| summary | CaseSummaryVO | AI 提取的摘要详情（见下） |
| score | CaseScoreVO | AI 评分详情（见下） |

---

### CaseSummaryVO

| 字段 | 类型 | 说明 |
|------|------|------|
| caseReason | String | 案由摘要 |
| disputeFocus | String | 争议焦点 |
| judgmentResult | String | 判决结果摘要 |
| keyPoints | String | 核心要点 |

---

### CaseScoreVO

| 字段 | 类型 | 说明 |
|------|------|------|
| importanceScore | Integer | 重要性评分（0-100） |
| influenceScore | Integer | 影响力评分（0-100） |
| referenceScore | Integer | 参考价值评分（0-100） |
| totalScore | Integer | 综合评分（0-100） |
| scoreReason | String | 评分理由 |
| scoreTags | String | 评分标签（逗号分隔） |

---

### BrowseHistoryVO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 浏览记录 ID |
| caseId | Long | 案例 ID |
| titleZh | String | 案例中文标题 |
| titleEn | String | 案例英文标题 |
| caseReason | String | 案由 |
| country | String | 所属国家/地区 |
| court | String | 审理法院 |
| caseType | Integer | 案件类型（见枚举） |
| importanceScore | Integer | 重要性评分 |
| createdAt | String | 浏览时间，格式 `yyyy-MM-ddTHH:mm:ss` |

---

### CaseTranslationRecordVO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 记录 ID |
| caseId | Long | 案例 ID |
| sourceLang | String | 原文语言（如 `en`） |
| targetLang | String | 目标语言（如 `zh`） |
| translatedContent | String | 翻译后内容 |
| status | Integer | 状态：0-待翻译，1-翻译中，2-已完成，3-失败 |
| aiModel | String | 使用的 AI 模型 |
| tokenUsed | Integer | 消耗 Token 数 |
| errorMsg | String | 失败时的错误信息 |
| createdAt | String | 创建时间 |
| updatedAt | String | 更新时间 |

---

### CaseSummaryRecordVO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 记录 ID |
| caseId | Long | 案例 ID |
| caseReason | String | 案由摘要 |
| disputeFocus | String | 争议焦点 |
| judgmentResult | String | 判决结果摘要 |
| keyPoints | String | 核心要点 |
| status | Integer | 状态：0-待提取，1-提取中，2-已完成，3-失败 |
| errorMsg | String | 失败时的错误信息 |
| createdAt | String | 创建时间 |
| updatedAt | String | 更新时间 |

---

### CaseScoreRecordVO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 记录 ID |
| caseId | Long | 案例 ID |
| importanceScore | Integer | 重要性评分 |
| influenceScore | Integer | 影响力评分 |
| referenceScore | Integer | 参考价值评分 |
| totalScore | Integer | 综合评分 |
| scoreReason | String | 评分理由 |
| scoreTags | String | 评分标签（逗号分隔） |
| status | Integer | 状态：0-待评分，1-评分中，2-已完成，3-失败 |
| errorMsg | String | 失败时的错误信息 |
| createdAt | String | 创建时间 |
| updatedAt | String | 更新时间 |
