# AI Starter 接入飞书通道实施计划

> **给智能体执行者：** 按任务逐项执行，完成后同步勾选状态并运行对应验证。

**目标：** 在 `ai-agent-spring-boot-starter` 中实现简易 OpenClaw 通道核心，并让 `module-feishu` 作为飞书机器人通道接入，使飞书消息可以正常聊天并调用 AI 技能。

**架构：** starter 提供通用 `AiChannelMessage`、`AiChannelReply`、`AiChannelHandler`，统一承接外部通道消息并调用 `AiAgentService` 与 `AiSkillRegistry`。飞书模块只做通道适配，实现 `FeishuAgentMessageHandler`，把飞书消息转成 starter 通道消息，再把 starter 回复发回飞书。

**技术栈：** Java 17、Spring Boot 3.4.5、Maven 多模块、AgentScope Harness 2.0.0-RC3、JUnit 5、AssertJ、Mockito。

## 全局约束

- 所有新增或修改的 `.md` 文档使用中文。
- 不编辑 `target/`、`dist/` 等生成产物。
- 不把真实飞书凭据或大模型 API Key 写入文档、日志、提交信息或聊天回复。
- `ai-agent-spring-boot-starter` 不依赖 `module-feishu`。
- `module-feishu` 可依赖 `ai-agent-spring-boot-starter`，作为飞书通道适配层接入通用智能体核心。

---

### Task 1: starter 通道核心

**文件：**
- 新建：`modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/channel/AiChannelMessage.java`
- 新建：`modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/channel/AiChannelReply.java`
- 新建：`modules/ai-agent-spring-boot-starter/src/main/java/com/xingju/starter/ai/channel/AiChannelHandler.java`
- 测试：`modules/ai-agent-spring-boot-starter/src/test/java/com/xingju/starter/ai/channel/AiChannelHandlerTest.java`

**接口：**
- 产出：`AiChannelMessage.of(String channel, String tenantId, String userId, String conversationId, String messageId, String text)`
- 产出：`AiChannelReply.text(String content)`
- 产出：`AiChannelHandler.handle(AiChannelMessage message): AiChannelReply`

- [x] 写失败测试：空消息返回提示；普通消息调用 `AiAgentService.chat`；`技能 skillName key=value` 调用 `AiSkillRegistry.call`。
- [x] 补充解析测试：支持带空格的引号参数、JSON 参数体、技能异常兜底。
- [x] 运行 starter 单测确认失败。
- [x] 实现通道模型、处理器、参数解析与异常兜底。
- [x] 运行 starter 单测确认通过。

### Task 2: 飞书通道适配器

**文件：**
- 新建：`modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAiChannelMessageHandler.java`
- 修改：`modules/module-feishu/module-feishu-core/pom.xml`
- 测试：`modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/agent/FeishuAiChannelMessageHandlerTest.java`

**接口：**
- 消费：`AiChannelHandler#handle(AiChannelMessage)`
- 产出：`FeishuAiChannelMessageHandler implements FeishuAgentMessageHandler`

- [x] 写失败测试：飞书消息转成 AI 通道消息并回复飞书文本。
- [x] 运行飞书 core 单测确认失败。
- [x] 实现飞书适配器与依赖。
- [x] 运行飞书 core 单测确认通过。

### Task 3: 自动装配接入

**文件：**
- 修改：`modules/module-feishu/module-feishu-autoconfig/src/main/java/com/xingju/module/feishu/autoconfig/FeishuAutoConfiguration.java`
- 修改：`modules/module-feishu/module-feishu-autoconfig/pom.xml`
- 测试：`modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAiChannelAutoConfigurationTest.java`

**接口：**
- 消费：`AiChannelHandler`
- 产出：当存在 `AiChannelHandler` 且 `feishu.agent.ai-channel.enabled=true` 时，注册 `FeishuAiChannelMessageHandler`。

- [x] 写失败自动装配测试。
- [x] 补充默认保留原 gateway、显式关闭保留原 gateway 的自动装配测试。
- [x] 运行自动装配测试确认失败。
- [x] 增加条件 Bean 与独立启用开关。
- [x] 运行自动装配测试确认通过。

### Task 4: 飞书 CLI 能力发布为 AI 技能

**文件：**
- 新建：`modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuBitableCreateRecordAiSkill.java`
- 新建：`modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuDocumentCreateAiSkill.java`
- 新建：`modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuDocumentAppendAiSkill.java`
- 新建：`modules/module-feishu/module-feishu-core/src/main/java/com/xingju/module/feishu/agent/FeishuAiSkillSupport.java`
- 测试：`modules/module-feishu/module-feishu-core/src/test/java/com/xingju/module/feishu/agent/FeishuAiSkillTest.java`
- 测试：`modules/module-feishu/module-feishu-autoconfig/src/test/java/com/xingju/module/feishu/autoconfig/FeishuAiSkillAutoConfigurationTest.java`

- [x] 写失败测试：多维表格新增记录、文档创建、文档追加内容。
- [x] 实现飞书 CLI AI 技能。
- [x] 在飞书自动装配中发布技能 Bean。
- [x] 验证 `AiSkillRegistry` 能自动收集飞书技能。

### Task 5: 模块验证

- [x] 运行 `mvn -pl modules/ai-agent-spring-boot-starter test`。
- [x] 运行 `mvn -pl modules/module-feishu/module-feishu-core -am test`。
- [x] 运行 `mvn -pl modules/module-feishu/module-feishu-autoconfig -am test`。
- [x] 完成代码审查并修复重要反馈。
