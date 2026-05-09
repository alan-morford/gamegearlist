#!/bin/sh
# Gradle wrapper script
APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
JAVA_OPTS=""
exec "$JAVA_HOME/bin/java" \
  $JAVA_OPTS \
  -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
