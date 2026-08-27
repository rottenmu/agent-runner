#!/bin/bash
# Maven wrapper for this project
export JAVA_HOME="D:/.dev/zulu17.56.15-ca-jdk17.0.14-win_x64"
export M2_HOME="D:/.dev/apache-maven-3.6.3"
export MAVEN_OPTS="-Dmaven.multiModuleProjectDirectory=D:/codehub/agent_runner"

"$JAVA_HOME/bin/java" \
  -cp "$M2_HOME/boot/plexus-classworlds-2.6.0.jar" \
  -Dclassworlds.conf="$M2_HOME/bin/m2.conf" \
  -Dmaven.home="$M2_HOME" \
  -Dmaven.multiModuleProjectDirectory="D:/codehub/agent_runner" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
