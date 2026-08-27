#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
远程沙箱服务端（标准库实现，零依赖）。

协议（与 Java HttpRemoteSandboxBackend 对应）：
  POST /api/sandbox/execute
  body: {"argv": [...], "env": {...} | null, "stdin": "..." | null,
         "timeoutSeconds": int, "workdir": "..." | null}
  resp: {"exitCode": int, "stdout": "...", "stderr": "...",
         "timedOut": bool, "error": null|"..."}

  POST /api/sandbox/file
  body: {"op": "read|write|list|delete|exists", "path": "...", "content": "..." | null}
  resp: {"success": bool, "content": "..." | null, "entries": [...] | null,
         "exists": bool, "error": null|"..."}

安全：
  - 仅允许白名单可执行程序（默认 python3/python/python3.x；含 `code` 参数时校验）
  - 可选 Authorization: Bearer <token> 鉴权（启动参数 --token）
  - workdir 仅允许临时目录（--workdir，默认系统 TEMP）
  - timeoutSeconds 上限钳制（--max-timeout，默认 60）
  - 文件操作路径仅允许工作区内相对路径（禁止绝对路径/.. 逃逸）
  - 文件读取/写入大小上限（--max-file-bytes，默认 1MB）

用法：
  python3 scripts/sandbox-server.py [--port 8799] [--token xxx]
      [--workdir /tmp/sandbox] [--max-timeout 60] [--host 127.0.0.1]
"""
import argparse
import http.server
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import re

ALLOWED_EXECUTABLES = re.compile(r"^python([0-9.]*)?\.exe$|^python3$|^python$|^python[0-9.]*$")
BLOCKED_ARGS = ("--", "-m pip", "-i ", "-c socket" )

class SandboxHandler(http.server.BaseHTTPRequestHandler):
    server_version = "RemoteSandbox/1.0"

    @property
    def sandbox(self):
        return self.server.sandbox  # type: ignore[attr-defined]

    def log_message(self, fmt, *args):
        sys.stderr.write("[sandbox] %s\n" % (fmt % args))

    def do_POST(self):
        path = self.path.rstrip("/")
        if path == "/api/sandbox/execute":
            self._handle_execute()
        elif path == "/api/sandbox/file":
            self._handle_file()
        else:
            self._json(404, {"error": "not found"})

    def _read_json(self):
        length = int(self.headers.get("Content-Length", 0))
        return json.loads(self.rfile.read(length).decode("utf-8") or "{}")

    def _handle_execute(self):
        if not self._authorized():
            self._json(401, {"error": "unauthorized"})
            return
        try:
            body = self._read_json()
        except Exception as exc:
            self._json(400, {"error": "bad request: %s" % exc})
            return
        try:
            result = self.sandbox.execute(body)
            self._json(200, result)
        except Exception as exc:
            self._json(500, {"error": str(exc)})

    def _handle_file(self):
        if not self._authorized():
            self._json(401, {"error": "unauthorized"})
            return
        try:
            body = self._read_json()
        except Exception as exc:
            self._json(400, {"error": "bad request: %s" % exc})
            return
        try:
            result = self.sandbox.file_op(body)
            self._json(200, result)
        except Exception as exc:
            self._json(500, {"error": str(exc)})

    def _authorized(self):
        expected = self.sandbox.token
        if not expected:
            return True
        header = self.headers.get("Authorization", "")
        got = header[7:] if header.lower().startswith("bearer ") else ""
        return got == expected

    def _json(self, code, payload):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


class Sandbox:
    def __init__(self, workdir, max_timeout, max_file_bytes=1024 * 1024):
        self.workdir = workdir or tempfile.gettempdir()
        self.max_timeout = max(1, min(int(max_timeout or 60), 300))
        self.max_file_bytes = max(1024, int(max_file_bytes or 1024 * 1024))
        self.token = None

    def file_op(self, request):
        op = (request.get("op") or "").strip().lower()
        raw_path = request.get("path") or ""
        try:
            target = self._resolve_path(raw_path)
        except ValueError as exc:
            return self._file_fail(str(exc))
        try:
            if op == "read":
                return self._file_read(target)
            if op == "write":
                return self._file_write(target, request.get("content"))
            if op == "list":
                return self._file_list(target)
            if op == "delete":
                return self._file_delete(target)
            if op == "exists":
                return {"success": True, "content": None, "entries": None,
                        "exists": os.path.exists(target), "error": None}
            return self._file_fail("不支持的文件操作: %s" % op)
        except Exception as exc:
            return self._file_fail("文件操作失败: %s" % exc)

    def _resolve_path(self, raw_path):
        if not raw_path:
            return os.path.realpath(self.workdir)
        normalized = raw_path.replace("\\", "/")
        if normalized.startswith("/") or re.match(r"^[A-Za-z]:", normalized):
            raise ValueError("禁止绝对路径: %s" % raw_path)
        parts = [p for p in normalized.split("/") if p not in ("", ".")]
        if any(p == ".." for p in parts):
            raise ValueError("禁止路径越界: %s" % raw_path)
        if not parts:
            return os.path.realpath(self.workdir)
        base = os.path.realpath(self.workdir)
        target = os.path.realpath(os.path.join(base, *parts))
        if target != base and not target.startswith(base + os.sep):
            raise ValueError("路径越出工作区: %s" % raw_path)
        return target

    def _file_read(self, target):
        if not os.path.exists(target):
            return self._file_fail("文件不存在: %s" % os.path.basename(target))
        if not os.path.isfile(target):
            return self._file_fail("不是文件: %s" % os.path.basename(target))
        if os.path.getsize(target) > self.max_file_bytes:
            return self._file_fail("文件超过大小上限: %s" % os.path.basename(target))
        with open(target, "r", encoding="utf-8", errors="replace") as fh:
            return {"success": True, "content": fh.read(), "entries": None,
                    "exists": True, "error": None}

    def _file_write(self, target, content):
        data = (content or "").encode("utf-8")
        if len(data) > self.max_file_bytes:
            return self._file_fail("写入内容超过大小上限")
        parent = os.path.dirname(target)
        if parent:
            os.makedirs(parent, exist_ok=True)
        with open(target, "w", encoding="utf-8", errors="replace") as fh:
            fh.write(content or "")
        return {"success": True, "content": None, "entries": None,
                "exists": True, "error": None}

    def _file_list(self, target):
        if not os.path.exists(target):
            return self._file_fail("目录不存在: %s" % os.path.basename(target))
        if not os.path.isdir(target):
            return self._file_fail("不是目录: %s" % os.path.basename(target))
        entries = sorted(
            name + "/" if os.path.isdir(os.path.join(target, name)) else name
            for name in os.listdir(target)
        )
        return {"success": True, "content": None, "entries": entries,
                "exists": True, "error": None}

    def _file_delete(self, target):
        if os.path.realpath(target) == os.path.realpath(self.workdir):
            return self._file_fail("禁止删除工作区根目录")
        if not os.path.exists(target):
            return {"success": True, "content": None, "entries": None,
                    "exists": False, "error": None}
        try:
            if os.path.isdir(target) and not os.path.islink(target):
                shutil.rmtree(target)
            else:
                os.remove(target)
        except Exception as exc:
            return self._file_fail("删除被沙箱拒绝: %s" % exc)
        return {"success": True, "content": None, "entries": None,
                "exists": False, "error": None}

    @staticmethod
    def _file_fail(message):
        return {"success": False, "content": None, "entries": None,
                "exists": False, "error": message}

    def execute(self, request):
        argv = request.get("argv") or []
        if not argv:
            return self._fail("命令为空")
        executable = os.path.basename(argv[0])
        if not ALLOWED_EXECUTABLES.match(executable):
            return self._fail("命令被沙箱拒绝: %s" % argv[0])
        joined = " ".join(argv)
        for blocked in BLOCKED_ARGS:
            if blocked in joined:
                return self._fail("命令包含受限参数: %s" % blocked)
        env = dict(os.environ)
        env.update(request.get("env") or {})
        stdin_data = request.get("stdin")
        timeout = int(request.get("timeoutSeconds") or 10)
        timeout = max(1, min(timeout, self.max_timeout))
        workdir = self._resolve_workdir(request.get("workdir"))
        try:
            proc = subprocess.Popen(
                argv,
                stdin=subprocess.PIPE if stdin_data else subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                cwd=workdir,
                env=env,
            )
            out, err = proc.communicate(input=stdin_data.encode("utf-8") if stdin_data else None,
                                        timeout=timeout)
            return {
                "exitCode": proc.returncode,
                "stdout": out.decode("utf-8", "replace"),
                "stderr": err.decode("utf-8", "replace"),
                "timedOut": False,
                "error": None,
            }
        except subprocess.TimeoutExpired:
            proc.kill()
            try:
                out, err = proc.communicate(timeout=3)
            except Exception:
                out, err = b"", b""
            return {
                "exitCode": -1,
                "stdout": out.decode("utf-8", "replace"),
                "stderr": err.decode("utf-8", "replace") + "\n(timeout)",
                "timedOut": True,
                "error": None,
            }
        except Exception as exc:
            return self._fail("命令启动失败: %s" % exc)

    def _resolve_workdir(self, requested):
        if not requested:
            return self.workdir
        base = os.path.realpath(self.workdir)
        target = os.path.realpath(os.path.join(base, requested))
        if target == base or target.startswith(base + os.sep):
            return target
        return self.workdir

    @staticmethod
    def _fail(message):
        return {"exitCode": -2, "stdout": None, "stderr": None, "timedOut": False, "error": message}


class SandboxServer(http.server.ThreadingHTTPServer):
    def __init__(self, addr, sandbox):
        super().__init__(addr, SandboxHandler)
        self.sandbox = sandbox
        self.daemon_threads = True


def main():
    parser = argparse.ArgumentParser(description="Remote sandbox server (zero-dependency)")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8799)
    parser.add_argument("--token", default="")
    parser.add_argument("--workdir", default="")
    parser.add_argument("--max-timeout", type=int, default=60)
    parser.add_argument("--max-file-bytes", type=int, default=1024 * 1024)
    args = parser.parse_args()

    sandbox = Sandbox(args.workdir, args.max_timeout, args.max_file_bytes)
    sandbox.token = args.token
    try:
        server = SandboxServer((args.host, args.port), sandbox)
    except OSError as exc:
        print("[sandbox] 启动失败(端口被占用?): %s" % exc, file=sys.stderr)
        sys.exit(1)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    print("[sandbox] listening on http://%s:%s%s" % (args.host, args.port, SandboxHandlerServerPath()),
          flush=True)
    print("[sandbox] workdir=%s max_timeout=%d token=%s" % (sandbox.workdir, sandbox.max_timeout,
                                                             "***" if args.token else "(none)"), flush=True)
    try:
        while True:
            import time
            time.sleep(3600)
    except KeyboardInterrupt:
        server.shutdown()


def SandboxHandlerServerPath():
    return "/api/sandbox/execute"


if __name__ == "__main__":
    main()