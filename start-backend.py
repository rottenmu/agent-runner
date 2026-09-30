"""启动 agent-application 后端(9900)。脚本自身保持存活以挂住 java 子进程。"""
import os
import subprocess
import sys
import time

ROOT = r"D:\codehub\agent_runner"
JAR = os.path.join(ROOT, "agent-application", "target", "agent-application-1.0.0.jar")
JAVA = r"D:/.dev/zulu17.56.15-ca-jdk17.0.14-win_x64/bin/java.exe"
LOG = r"D:\codehub\agent_runner\tmp\verify\backend-run9.log"

if not os.path.exists(JAR):
    print("JAR NOT FOUND:", JAR, flush=True)
    sys.exit(2)

cmd = [JAVA,
       "--add-opens=java.base/java.nio=org.apache.arrow.memory.core,ALL-UNNAMED",
       "-jar", JAR,
       "--server.port=9900"]
env = dict(os.environ)
env["JAVA_HOME"] = r"D:/.dev/zulu17.56.15-ca-jdk17.0.14-win_x64"

print("starting:", " ".join(cmd), flush=True)
with open(LOG, "w", encoding="utf-8", errors="replace") as log:
    p = subprocess.Popen(cmd, cwd=ROOT, env=env,
                         stdout=log, stderr=subprocess.STDOUT,
                         creationflags=subprocess.CREATE_NO_WINDOW)
    print("java pid:", p.pid, flush=True)
    # 挂住进程:等待直至退出(正常服务永远不退出)
    rc = p.wait()
    print("java exited rc=", rc, flush=True)