#!/bin/bash
# Build "jarcraft-alpha1.0.0.jar" (Java 8 bytecode, class version 52)
set -e
source /home/z/my-project/scripts/env.sh
ROOT=/home/z/my-project/jarcraft-game
cd "$ROOT"

rm -rf classes manifest.mf
mkdir -p classes
"$JAVA_HOME/bin/javac" --release 8 -Xlint:-options -d classes src/com/jarcraft/*.java

cat > manifest.mf <<'EOF'
Manifest-Version: 1.0
Main-Class: com.jarcraft.Main
Implementation-Title: jarcraft
Implementation-Version: alpha 1.0.0
EOF

"$JAVA_HOME/bin/jar" cfm "jarcraft-alpha1.0.0.jar" manifest.mf -C classes .
cp -f "jarcraft-alpha1.0.0.jar" "/home/z/my-project/download/jarcraft-alpha1.0.0.jar"

# Verify class version
"$JAVA_HOME/bin/javap" -v -cp classes com.jarcraft.Main | grep "major version"
ls -la "jarcraft-alpha1.0.0.jar"
