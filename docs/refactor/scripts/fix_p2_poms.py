#!/usr/bin/env python3
"""P2 pom 修正：starter -> framework-ai 改名与模块登记。"""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def edit(path, repls):
    p = os.path.join(ROOT, path)
    if not os.path.exists(p):
        print(f"! 缺失: {path}")
        return
    t = open(p, encoding="utf-8").read()
    orig = t
    for old, new in repls:
        if old not in t:
            print(f"  [warn] 未命中: {path} <- {old!r}")
        t = t.replace(old, new)
    if t != orig:
        open(p, "w", encoding="utf-8", newline="\n").write(t)
        print(f"  ✓ {path}")


# 1) framework-ai 自身：parent 与 artifactId
edit("framework/framework-ai/pom.xml", [
    ("    <artifactId>modules</artifactId>\n        <version>1.0.0</version>",
     "    <artifactId>framework</artifactId>\n        <version>1.0.0</version>"),
    ("    <artifactId>agent-spring-boot-starter</artifactId>",
     "    <artifactId>framework-ai</artifactId>"),
])

# 2) 8 个下游 core 依赖
for m in ["agent-datasource/module-datasource-core",
          "agent-harness/agent-harness-core",
          "agent-intent/agent-intent-core",
          "agent-rag/agent-rag-core",
          "agent-trace/agent-trace-core",
          "module-feishu/module-feishu-core",
          "module-security/module-security-core",
          "module-tools/module-tools-core"]:
    edit(f"modules/{m}/pom.xml", [
        ("            <artifactId>agent-spring-boot-starter</artifactId>",
         "            <artifactId>framework-ai</artifactId>"),
    ])

# 3) modules 聚合移除模块声明
edit("modules/pom.xml", [
    ("        <module>agent-spring-boot-starter</module>\n", ""),
])

# 4) 根 pom dependencyManagement
edit("pom.xml", [
    ("                <artifactId>agent-spring-boot-starter</artifactId>",
     "                <artifactId>framework-ai</artifactId>"),
])

# 5) framework 聚合新增 framework-ai 模块
edit("framework/pom.xml", [
    ("        <module>framework-autoconfig</module>\n    </modules>",
     "        <module>framework-autoconfig</module>\n        <module>framework-ai</module>\n    </modules>"),
])
print("done")
