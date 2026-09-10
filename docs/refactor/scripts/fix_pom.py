# -*- coding: utf-8 -*-
"""修正合并后的 pom：删除自引用幽灵依赖、改写跨模块引用、重建标准缩进。"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

CORE_POMS = [
    "modules/agent-auth/module-auth-core",
    "modules/agent-datasource/module-datasource-core",
    "modules/agent-harness/agent-harness-core",
    "modules/agent-intent/agent-intent-core",
    "modules/agent-memory/agent-memory-core",
    "modules/agent-rag/agent-rag-core",
    "modules/agent-trace/agent-trace-core",
    "modules/module-feishu/module-feishu-core",
    "modules/module-security/module-security-core",
    "modules/module-sys/module-sys-core",
    "modules/module-tools/module-tools-core",
]

# 需要整块删除的自引用幽灵依赖
DROP = {
    "modules/agent-intent/agent-intent-core": {"module-intent-core"},
    "modules/agent-memory/agent-memory-core": {"module-agent-memory-core"},
}

# 需要改写的跨模块引用 old -> new
REWRITE = {"module-auth-autoconfig": "module-auth-core"}


def blocks(text):
    return re.findall(r"<dependency>.*?</dependency>", text, re.S)


def artifact(block):
    m = re.search(r"<artifactId>\s*(.*?)\s*</artifactId>", block, re.S)
    return m.group(1) if m else None


def reformat(text):
    """重建第一个 <dependencies> 块，统一为 4/8/12 空格缩进。"""
    m = re.search(r"( *)<dependencies>(.*?)</dependencies>", text, re.S)
    if not m:
        return text
    base = m.group(1)
    keep, dropped = [], []
    for b in blocks(m.group(2)):
        aid = artifact(b)
        drop_set = DROP.get(current, set())
        if aid in drop_set:
            dropped.append(aid)
            continue
        lines = [ln.strip() for ln in b.strip().splitlines() if ln.strip()]
        inner = lines[1:-1]
        if any("<dependency>" in ln for ln in inner):
            inner = []  # 异常块，放弃重排
        keep.append(base + "    <dependency>")
        for ln in inner:
            m2 = re.match(r"<artifactId>\s*(.*?)\s*</artifactId>", ln)
            if m2 and m2.group(1) in REWRITE:
                ln = f"<artifactId>{REWRITE[m2.group(1)]}</artifactId>"
            keep.append(base + "        " + ln)
        keep.append(base + "    </dependency>")
    body = "\n".join(keep)
    out = text[: m.start()] + base + "<dependencies>\n" + body + "\n" + base + "</dependencies>" + text[m.end():]
    return out, dropped


for rel in CORE_POMS:
    global current
    current = rel
    path = os.path.join(ROOT, rel, "pom.xml")
    if not os.path.exists(path):
        continue
    text = open(path, encoding="utf-8").read()
    new_text, dropped = reformat(text)
    if new_text != text:
        open(path, "w", encoding="utf-8", newline="\n").write(new_text)
    if dropped:
        print(f"{rel}: 删除自引用 {sorted(dropped)}")
    else:
        print(f"{rel}: 重排完成")
