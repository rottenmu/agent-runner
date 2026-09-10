# -*- coding: utf-8 -*-
"""把 11 个 autoconfig 子模块合并进各自的 core 模块。

用法:
    python merge_autoconfig.py check    # 预演，只报冲突
    python merge_autoconfig.py move     # 真正移动文件
    python merge_autoconfig.py pom      # 合并 pom 依赖
"""
import os
import re
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

PAIRS = [
    ("modules/agent-auth/module-auth-autoconfig", "modules/agent-auth/module-auth-core", "module-auth-core"),
    ("modules/agent-datasource/module-datasource-autoconfig", "modules/agent-datasource/module-datasource-core", "module-datasource-core"),
    ("modules/agent-harness/agent-harness-autoconfig", "modules/agent-harness/agent-harness-core", "agent-harness-core"),
    ("modules/agent-intent/agent-intent-autoconfig", "modules/agent-intent/agent-intent-core", "agent-intent-core"),
    ("modules/agent-memory/agent-memory-autoconfig", "modules/agent-memory/agent-memory-core", "agent-memory-core"),
    ("modules/agent-rag/rag-autoconfig", "modules/agent-rag/agent-rag-core", "agent-rag-core"),
    ("modules/agent-trace/agent-trace-autoconfig", "modules/agent-trace/agent-trace-core", "agent-trace-core"),
    ("modules/module-feishu/module-feishu-autoconfig", "modules/module-feishu/module-feishu-core", "module-feishu-core"),
    ("modules/module-security/module-security-autoconfig", "modules/module-security/module-security-core", "module-security-core"),
    ("modules/module-sys/module-sys-autoconfig", "modules/module-sys/module-sys-core", "module-sys-core"),
    ("modules/module-tools/module-tools-autoconfig", "modules/module-tools/module-tools-core", "module-tools-core"),
]


def plan_moves(ac_dir, core_dir):
    """返回 [(src_abs, dst_abs), ...]，只覆盖 src/ 下的文件。"""
    src_root = os.path.join(ROOT, ac_dir, "src")
    dst_root = os.path.join(ROOT, core_dir, "src")
    moves = []
    if not os.path.isdir(src_root):
        return moves
    for dirpath, _dirnames, filenames in os.walk(src_root):
        rel = os.path.relpath(dirpath, src_root)
        target_dir = dst_root if rel == "." else os.path.join(dst_root, rel)
        for name in filenames:
            moves.append((os.path.join(dirpath, name), os.path.join(target_dir, name)))
    return moves


def cmd_check():
    total = 0
    conflicts = 0
    for ac, core, _aid in PAIRS:
        moves = plan_moves(ac, core)
        bad = [(s, d) for s, d in moves if os.path.exists(d)]
        print(f"{ac}: {len(moves)} 个文件, 冲突 {len(bad)}")
        for _s, d in bad:
            print(f"    冲突 -> {os.path.relpath(d, ROOT)}")
        total += len(moves)
        conflicts += len(bad)
    print(f"\n合计 {total} 个文件待移动, {conflicts} 个冲突")
    return conflicts


def cmd_move():
    moved = 0
    for ac, core, _aid in PAIRS:
        for s, d in plan_moves(ac, core):
            if os.path.exists(d):
                print(f"跳过(已存在): {os.path.relpath(d, ROOT)}")
                continue
            os.makedirs(os.path.dirname(d), exist_ok=True)
            shutil.move(s, d)
            moved += 1
    print(f"已移动 {moved} 个文件")


def parse_deps(pom_text):
    """提取 <dependencies> 下的 <dependency> 块。"""
    m = re.search(r"<dependencies>(.*?)</dependencies>", pom_text, re.S)
    if not m:
        return []
    return re.findall(r"<dependency>.*?</dependency>", m.group(1), re.S)


def dep_artifact(block):
    m = re.search(r"<artifactId>(.*?)</artifactId>", block, re.S)
    return m.group(1).strip() if m else None


def cmd_pom():
    for ac, core, core_aid in PAIRS:
        ac_pom = os.path.join(ROOT, ac, "pom.xml")
        core_pom = os.path.join(ROOT, core, "pom.xml")
        ac_text = open(ac_pom, encoding="utf-8").read()
        core_text = open(core_pom, encoding="utf-8").read()

        ac_deps = parse_deps(ac_text)
        core_deps = parse_deps(core_text)
        have = {dep_artifact(b) for b in core_deps}

        added = []
        for block in ac_deps:
            aid = dep_artifact(block)
            if aid is None or aid == core_aid or aid in have:
                continue
            # 归一化缩进：整体缩进 8 空格
            lines = block.splitlines()
            normalized = "\n".join(("        " + ln.strip()) if ln.strip() else "" for ln in lines)
            added.append(normalized)
            have.add(aid)

        if not added:
            print(f"{core}: 无需新增依赖")
            continue

        insert = "\n\n".join(added)
        core_text = core_text.replace(
            "</dependencies>",
            insert + "\n    </dependencies>",
            1,
        )
        open(core_pom, "w", encoding="utf-8", newline="\n").write(core_text)
        print(f"{core}: 新增 {len(added)} 个依赖 -> {sorted(have - {dep_artifact(b) for b in core_deps})}")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "check"
    if cmd == "check":
        sys.exit(1 if cmd_check() else 0)
    elif cmd == "move":
        cmd_move()
    elif cmd == "pom":
        cmd_pom()
    else:
        print(__doc__)
