# 飞书 CLI 执行层设计

## 背景

`module-feishu` 已经包含飞书配置、凭据、Channel SDK、消息回复和用户映射能力。新的 CLI 执行层继续放在现有 `module-feishu` 内，遵循当前 `module-feishu-core + module-feishu-autoconfig` 的插件式结构，不新增独立 Spring Boot 应用。

飞书 CLI 支持两种接入形态：

- 标准 CLI 模式：通过 `npx @larksuite/cli@latest` 调用，适合本地开发和快速验证。
- 自研 Wrapper CLI 模式：通过配置指向企业自研编译后的增强版 CLI 可执行文件，适合接入飞书 CLI 的 Go 扩展机制，例如 `Credential`、`Transport`、`Restrict`、`Observer`、`Wrap`、`On`。

为降低 CLI 快捷命令变更风险，本设计优先封装 `api METHOD PATH --params <json> --data <json> --format json` 这一通用调用形态，业务 Service 只负责把 Java DTO 转换为标准请求对象。

## 目标

- Java 统一封装飞书 CLI 进程调用，处理进程创建、输出解析、超时、异常捕获。
- 支持标准 `npx @larksuite/cli@latest` 和自研 Wrapper CLI 可执行文件两种命令来源。
- 提供多维表格、文档、日历、任务四类标准业务方法。
- 所有 CLI 入参使用 DTO 或结构化 Map，由 Jackson 序列化 JSON，不在业务代码中硬编码 JSON 字符串。
- 每次 CLI 调用记录日志、耗时、命令摘要、返回结果，失败自动重试 1 次。
- 通过 Spring Boot 自动装配提供 starter 式接入，默认可配置启停。

## 非目标

- 不在本次实现真实登录飞书 CLI，也不托管 CLI 凭据文件。
- 不在本次实现 Go Wrapper CLI 本身；本次只让 Java 调用层可以配置并调用自研 Wrapper CLI。
- 不在 Java 进程内复刻飞书 CLI 的 `Credential`、`Transport`、`Restrict`、`Observer`、`Wrap`、`On` 扩展机制。
- 不把 CLI 输出强绑定为完整飞书 OpenAPI 响应模型，只提供原始 JSON 节点和常用字段读取能力。
- 不新增前端页面。
- 不替换已有 SDK/Channel 能力；CLI 层作为补充执行通道。

## 模块结构

### core

新增 `com.zimo.module.feishu.cli` 包：

- `FeishuCliCommandRequest`：通用 CLI 调用请求，包含方法、路径、params、data、超时等。
- `FeishuCliCommandResult`：通用 CLI 调用结果，包含成功标记、退出码、stdout、stderr、JSON 结果、耗时、尝试次数。
- `FeishuCliExecutor`：执行器接口，供业务 Service 依赖。
- `FeishuCliException`：CLI 执行失败异常。
- `FeishuCliCallLogEntity`、`FeishuCliCallLogMapper`、`FeishuCliCallLogService`：CLI 调用日志落库能力。

新增 `com.zimo.module.feishu.cli.bitable`：

- 多维表格 DTO。
- `FeishuBitableCliService`：新增记录、查询数据、更新单元格。

新增 `com.zimo.module.feishu.cli.document`：

- 文档 DTO。
- `FeishuDocumentCliService`：新建文档、追加内容、获取文档链接。

新增 `com.zimo.module.feishu.cli.calendar`：

- 日历 DTO。
- `FeishuCalendarCliService`：创建日程、添加参会人。

新增 `com.zimo.module.feishu.cli.task`：

- 任务 DTO。
- `FeishuTaskCliService`：新建待办、指派负责人。

### autoconfig

新增：

- `FeishuCliProperties`：配置前缀 `feishu.cli`，包含启停、命令模式、命令路径、包名、超时、重试次数、工作目录、日志开关、允许的业务域。
- `ProcessFeishuCliExecutor`：基于 `ProcessBuilder` 的真实 CLI 执行器。
- 在 `FeishuAutoConfiguration` 中注册 CLI 执行器、日志服务、四类业务 Service 和 Mapper 扫描。

## API 路径约定

业务 Service 内部统一走飞书 OpenAPI 路径：

- 多维表格新增记录：`POST /open-apis/bitable/v1/apps/{appToken}/tables/{tableId}/records`
- 多维表格查询数据：`GET /open-apis/bitable/v1/apps/{appToken}/tables/{tableId}/records`
- 多维表格更新单元格：`PUT /open-apis/bitable/v1/apps/{appToken}/tables/{tableId}/records/{recordId}`
- 文档新建：`POST /open-apis/docx/v1/documents`
- 文档追加内容：`POST /open-apis/docx/v1/documents/{documentId}/blocks/{blockId}/children`
- 文档获取链接：优先从创建或查询结果中读取 `url` 字段，必要时由文档 token 组装标准链接。
- 日历创建日程：`POST /open-apis/calendar/v4/calendars/{calendarId}/events`
- 日历添加参会人：`POST /open-apis/calendar/v4/calendars/{calendarId}/events/{eventId}/attendees`
- 任务新建待办：`POST /open-apis/task/v2/tasks`
- 任务指派负责人：`POST /open-apis/task/v2/tasks/{taskGuid}/members`

## CLI 调用设计

`ProcessFeishuCliExecutor` 负责把请求转换为命令。标准 CLI 模式使用：

```text
npx @larksuite/cli@latest api <METHOD> <PATH> --format json --params <json> --data <json>
```

自研 Wrapper CLI 模式使用：

```text
<wrapper-cli-path> api <METHOD> <PATH> --format json --params <json> --data <json>
```

规则：

- `mode=NPX` 时命令前缀为 `command + packageName`，默认 `npx @larksuite/cli@latest`。
- `mode=WRAPPER` 时命令前缀为 `wrapperPath`，例如 `D:/tools/my-lark-cli.exe`。
- Java 层先按 `allowedBusinessTypes` 做业务域白名单校验；真正的 CLI 内部命令裁剪、身份限定、审批、限流由自研 Wrapper CLI 的 `Restrict` 和 `Wrap` 承担。
- `params` 为空时不传 `--params`。
- `data` 为空时不传 `--data`。
- 命令参数使用 `ProcessBuilder` 参数列表传递，不拼接 shell 字符串。
- 超时后销毁进程并返回失败结果。
- stdout 解析为 Jackson `JsonNode`；解析失败时保留原始 stdout。
- 非 0 退出码、超时、进程异常均视为失败。
- 默认最多尝试 2 次，即首次失败后自动重试 1 次。

## 日志设计

新增表 `ps_feishu_cli_call_log`：

- `id`
- `business_type`
- `method`
- `api_path`
- `command_summary`
- `params_json`
- `data_json`
- `success`
- `exit_code`
- `attempts`
- `cost_millis`
- `stdout`
- `stderr`
- `error_message`
- `create_time`

日志服务要求：

- 日志失败不影响主流程。
- stdout、stderr、入参 JSON 需要长度截断，避免大字段无限增长。
- 失败重试后记录最终结果和尝试次数。

## 自动装配

配置示例：

```yaml
feishu:
  cli:
    enabled: true
    mode: NPX
    command: npx
    package-name: "@larksuite/cli@latest"
    wrapper-path:
    timeout-seconds: 30
    retry-times: 1
    log-enabled: true
    allowed-business-types:
      - bitable
      - document
      - calendar
      - task
```

自研 Wrapper CLI 示例：

```yaml
feishu:
  cli:
    enabled: true
    mode: WRAPPER
    wrapper-path: D:/tools/my-lark-cli.exe
    timeout-seconds: 30
    retry-times: 1
    allowed-business-types:
      - bitable
      - document
```

自动装配规则：

- `feishu.enabled=false` 时整体不启用。
- `feishu.cli.enabled=false` 时 CLI 执行层不注册。
- 允许业务方覆盖 `FeishuCliExecutor`，用于测试、私有 CLI 包装或远程执行。
- 有 `DataSource` 时自动检查 `ps_feishu_cli_call_log` 表。
- 当 `mode=WRAPPER` 且 `wrapper-path` 为空时，启动阶段保留 Bean 注册，但执行时返回失败结果并记录配置错误。

## 测试策略

- core 单元测试使用 fake executor，不真实调用 `npx`。
- 测试覆盖命令请求构造、业务 DTO 转换、失败重试、超时结果、日志记录和字段截断。
- autoconfig 测试覆盖 `feishu.cli.enabled=true/false` 下 Bean 注册情况。
- 模块验证命令：

```text
mvn -pl modules/module-feishu/module-feishu-core -am test
mvn -pl modules/module-feishu/module-feishu-autoconfig -am test
```

## 风险与处理

- 本地未安装 Node.js 或 npx：真实执行器返回失败结果，记录错误日志；业务环境需自行安装。
- CLI 登录态缺失：由 CLI stderr 返回，执行器不吞异常，日志保留错误信息。
- 自研 Wrapper CLI 未编译或路径错误：执行器返回失败结果，日志记录 `wrapper-path` 配置错误。
- Java 层白名单与 Wrapper CLI `Restrict` 不一致：Java 白名单作为第一道边界，Wrapper `Restrict` 作为最终边界，二者都通过才允许执行。
- OpenAPI 路径或 CLI 参数未来变化：业务 Service 只依赖通用 API 调用对象，后续可集中调整执行器。
- 输出结构差异：Service 返回通用结果对象，调用方可读取原始 JSON，减少强绑定。
