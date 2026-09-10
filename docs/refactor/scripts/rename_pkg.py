#!/usr/bin/env python3
"""包名批量替换：com.zimo.starter(.ai) -> com.zimo.framework(.ai)。

只处理源码与资源文本文件，跳过 target / .git / tmp / node_modules。
替换顺序：先长前缀 com.zimo.starter.ai，再短前缀 com.zimo.starter。
"""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SKIP_DIRS = {"target", ".git", "tmp", "node_modules", ".codegraph", "dist", "build"}
EXTS = {".java", ".imports", ".factories", ".xml", ".yml", ".yaml",
        ".properties", ".md", ".txt", ".json"}

# 按长度降序，避免短前缀先替换导致长前缀失配
PAIRS = [
    ("com.zimo.starter.ai", "com.zimo.framework.ai"),
    ("com.zimo.starter", "com.zimo.framework"),
    ("com/zimo/starter/ai", "com/zimo/framework/ai"),
    ("com/zimo/starter", "com/zimo/framework"),
]


# 历史计划文档保留原貌，不做替换（记录的是当时的设计决策）
EXCLUDE_REL_PREFIX = ("docs" + os.sep, ".workbuddy" + os.sep)


def should_skip(path):
    rel = os.path.relpath(path, ROOT)
    parts = set(rel.split(os.sep))
    if parts & SKIP_DIRS:
        return True
    return rel.startswith(EXCLUDE_REL_PREFIX)


def main():
    dry = "--apply" not in sys.argv
    changed_files = []
    total = 0

    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        if should_skip(dirpath):
            continue
        for fn in filenames:
            ext = os.path.splitext(fn)[1]
            if ext not in EXTS:
                continue
            fp = os.path.join(dirpath, fn)
            try:
                with open(fp, encoding="utf-8") as f:
                    text = f.read()
            except (UnicodeDecodeError, OSError):
                continue
            new_text = text
            hits = 0
            for old, new in PAIRS:
                c = new_text.count(old)
                if c:
                    hits += c
                    new_text = new_text.replace(old, new)
            if hits and new_text != text:
                changed_files.append((os.path.relpath(fp, ROOT), hits))
                total += hits
                if not dry:
                    with open(fp, "w", encoding="utf-8", newline="\n") as f:
                        f.write(new_text)

    print(f"{'预演' if dry else '已应用'}: {len(changed_files)} 个文件, {total} 处替换")
    for rel, hits in sorted(changed_files)[:15]:
        print(f"  {hits:>4}  {rel}")
    if len(changed_files) > 15:
        print(f"  ... 其余 {len(changed_files) - 15} 个文件")
    return 0


if __name__ == "__main__":
    sys.exit(main())
