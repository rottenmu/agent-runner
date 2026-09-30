"""Maven 编译封装：绕过坏掉的 bash shim 环境，直接用 java 启动 Maven。

用法: python mcp-build-run.py [额外参数...]
输出实时写入同目录 mcp-build.log，进程退出码即 Maven 退出码。
"""
import os
import subprocess
import sys
import time

ROOT = r"D:\codehub\agent_runner"
JAVA = r"D:/.dev/zulu17.56.15-ca-jdk17.0.14-win_x64/bin/java.exe"
CP = r"D:/.dev/apache-maven-3.6.3/boot/plexus-classworlds-2.6.0.jar"
M2_CONF = r"D:/.dev/apache-maven-3.6.3/bin/m2.conf"
M2_HOME = r"D:/.dev/apache-maven-3.6.3"
LOG = os.path.join(ROOT, "mcp-build.log")

def main():
    args = sys.argv[1:] or ["-pl", "agent-application", "-am", "package", "-DskipTests"]
    cmd = [JAVA, "-cp", CP,
           "-Dclassworlds.conf=" + M2_CONF,
           "-Dmaven.home=" + M2_HOME,
           "-Dmaven.multiModuleProjectDirectory=" + ROOT,
           "org.codehaus.plexus.classworlds.launcher.Launcher"] + args
    env = dict(os.environ)
    env["JAVA_HOME"] = r"D:/.dev/zulu17.56.15-ca-jdk17.0.14-win_x64"
    env["M2_HOME"] = M2_HOME

    print("CMD:", " ".join(cmd), flush=True)
    t0 = time.time()
    with open(LOG, "w", encoding="utf-8", errors="replace") as log:
        p = subprocess.Popen(cmd, cwd=ROOT, env=env,
                             stdout=log, stderr=subprocess.STDOUT,
                             creationflags=subprocess.CREATE_NO_WINDOW)
        rc = p.wait()
    print(f"DONE rc={rc} elapsed={time.time() - t0:.1f}s", flush=True)
    return rc

if __name__ == "__main__":
    sys.exit(main())