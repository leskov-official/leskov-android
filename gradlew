#!/usr/bin/env sh
# LESKOV source exports from Google AI Studio did not include Gradle's signed wrapper jar.
# Use a verified Gradle 9.3.1 installed in PATH, or run the provided GitHub Actions workflow.
set -eu
if [ -f "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" ]; then
  exec java -classpath "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
printf '%s\n' 'Gradle is not installed. Install Gradle 9.3.1 or open this project in Android Studio.' \
               'Once Gradle is installed, run: gradle wrapper --gradle-version 9.3.1' >&2
exit 127
