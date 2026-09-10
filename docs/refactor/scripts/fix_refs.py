# -*- coding: utf-8 -*-
"""1) 从业务聚合 pom 移除 autoconfig 模块声明
   2) 把外部 pom 中对 autoconfig 的依赖改写为对应 core
"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# autoconfig artifactId -> core artifactId
MAP = {
    "module-sys-autoconfig": "module-sys-core",
    "module-auth-autoconfig": "module-auth-core",
    "module-ai-autoconfig": "agent-harness-core",
    "agent-harness-autoconfig": "agent-harness-core",
    "module-feishu-autoconfig": "module-feishu-core",
    "module-datasource-autoconfig": "module-datasource-core",
    "rag-autoconfig": "agent-rag-core",
    "agent-intent-autoconfig": "agent-intent-core",
    "module-tools-autoconfig": "module-tools-core",
    "module-security-autoconfig": "module-security-core",
    "agent-trace-autoconfig": "agent-trace-core",
    "agent-memory-autoconfig": "agent-memory-core",
}


def step1_remove_module_decls():
    """业务聚合 pom 中的 <module>xxx-autoconfig</module> 行"""
    changed = []
    for name in sorted(os.listdir(os.path.join(ROOT, "modules"))):
        pom = os.path.join(ROOT, "modules", name, "pom.xml")
        if not os.path.exists(pom):
            continue
        text = open(pom, encoding="utf-8").read()
        new = re.sub(r"\n\s*<module>[^<]*autoconfig</module>", "", text)
        if new != text:
            open(pom, "w", encoding="utf-8", newline="\n").write(new)
            changed.append(name)
    print("已移除 autoconfig 模块声明:", changed)


def step2_rewrite_refs():
    targets = [
        "agent-application/pom.xml",
        "modules/agent-memory/agent-memory-application/pom.xml",
    ]
    for rel in targets:
        path = os.path.join(ROOT, rel)
        if not os.path.exists(path):
            continue
        text = open(path, encoding="utf-8").read()
        hits = []
        for old, new in MAP.items():
            pattern = r"(<artifactId>\s*)%s(\s*</artifactId>)" % re.escape(old)
            found = re.findall(pattern, text)
            if found:
                hits.append(f"{old} -> {new}")
            text = re.sub(pattern, r"\g<1>%s\g<2>" % new, text)
        open(path, "w", encoding="utf-8", newline="\n").write(text)
        print(f"{rel}: {len(hits)} 处改写")
        for h in hits:
            print("    ", h)


if __name__ == "__main__":
    step1_remove_module_decls()
    step2_rewrite_refs()
