#!/bin/bash
# Maven Wrapper script - downloads and uses Maven from temp directory

# Detect Java
if [ -z "$JAVA_HOME" ]; then
    JAVA="java"
else
    JAVA="$JAVA_HOME/bin/java"
fi

# Check if Maven is already downloaded
MVN_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip"
MVN_DIR="/tmp/apache-maven-3.9.6"

if [ ! -d "$MVN_DIR/bin" ]; then
    echo "Downloading Maven..."
    rm -f /tmp/maven.zip
    curl -sL "$MVN_URL" -o /tmp/maven.zip
    rm -rf "$MVN_DIR"
    unzip -q /tmp/maven.zip -d /tmp
    rm /tmp/maven.zip
fi

# Run Maven
exec "$MVN_DIR/bin/mvn" "$@"
