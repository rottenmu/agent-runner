# agent-harness / agent-memory 上传 GitHub 操作指引

> 日期：2026-08-22 ｜ 目标仓库：github.com/rottenmu/agent-harness、github.com/rottenmu/agent-memory  
> 背景：GitHub App 连接器无写权限（403）、系统旧 token 已过期（401），故改用网页/本地命令行上传。  
> 本地代码已备好：`tmp/repos/agent-harness`（161 文件，模块已重命名为 agent-harness-core/autoconfig）、`tmp/repos/agent-memory`（57 文件），均已 git commit（main 分支）。

## 方式一：网页拖拽上传（推荐，免命令行）

1. 打开 <https://github.com/rottenmu/agent-harness> → 点 **Add file** → **Upload files**
2. 进入本地目录 `D:\codehub\agent_runner\tmp\repos\agent-harness`，全选以下内容拖入网页：
   - `pom.xml`、`agent-harness-core/`、`agent-harness-autoconfig/`
   - ⚠️ 不拖 `.git/`（网页不支持）；GitHub 网页单次上限 **100 个文件**，本仓库 161 个文件需**分 2 次**（如先拖 agent-harness-core，再拖其余）
3. 填写 Commit message：`init: agent-harness module code split from agent_runner platform` → **Commit changes**
4. 同法操作 agent-memory（57 文件，一次可传完）：<https://github.com/rottenmu/agent-memory>
   - 内容：`pom.xml`、`module-agent-memory-core/`、`module-agent-memory-autoconfig/`

## 方式二：本地命令行推送（有 PAT 或账号密码时）

```bash
# agent-harness
cd /d/codehub/agent_runner/tmp/repos/agent-harness
git remote add origin https://github.com/rottenmu/agent-harness.git
git push -u origin main
# 提示输入：用户名 rottenmu，密码处粘贴 PAT（ghp_ 开头）或账号密码

# agent-memory
cd /d/codehub/agent_runner/tmp/repdos/agent-memory
git remote add origin https://github.com/rottenmu/agent-memory.git
git push -u origin main
```

> 若网页已创建过 README/main 分支，本地 `git pull origin main --rebase` 后再 push 即可合并。

## 上传内容核对

| 仓库            | 模块                                                        | 文件数 | 说明                                                       |
| ------------- | --------------------------------------------------------- | --- | -------------------------------------------------------- |
| agent-harness | agent-harness-core + agent-harness-autoconfig             | 161 | 智能体管理装配中心/遥测/工作流/插件管理 REST（2026-08-22 由 module-ai-* 重命名） |
| agent-memory  | module-agent-memory-core + module-agent-memory-autoconfig | 57  | 四层记忆 L0~L3 / OLTP+OLAP / ETL / 对话事件流                     |

> 备注：两个模块 pom 的 Maven parent 均为 agent_runner 根 pom，独立构建需先安装父 pom 或改独立 parent（README 中已注明）。
