#!/usr/bin/env bash
# The fixture deliberately uses no KBuild conventions or build platform.
set -euo pipefail
version=${1:?usage: verify-consumer.sh VERSION}
repository="$(pwd)/build/staging-repo"
./gradlew -p consumer run "-Pconsumer.repository=$repository" "-Pconsumer.version=$version"
./gradlew -p consumer run "-Pconsumer.repository=$repository" "-Pconsumer.version=$version" -Pconsumer.pomOnly=true
if [[ $# -gt 1 ]]; then
  ./gradlew -p consumer run "-Pconsumer.version=$version" "-Pconsumer.githubRepository=$2"
fi
