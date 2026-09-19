#!/bin/bash
# GUI smoke test under Xvfb: launch game, screenshot, kill.
set -u
source /home/z/my-project/scripts/env.sh
export DISPLAY=:99
export LD_LIBRARY_PATH=/home/z/my-project/toolchain/usr/lib/x86_64-linux-gnu:${LD_LIBRARY_PATH:-}

Xvfb :99 -screen 0 1024x768x24 &>/tmp/xvfb.log &
XVPID=$!
sleep 2

java -jar /home/z/my-project/download/jarcraft-alpha1.0.0.jar &>/tmp/game.log &
GPID=$!
sleep 8
java -cp /tmp Shot /tmp/game_shot.png &>/tmp/shot.log
sleep 0.5
kill $GPID 2>/dev/null
sleep 0.5
kill $XVPID 2>/dev/null

echo "---game log---"
cat /tmp/game.log
echo "---shot---"
cat /tmp/shot.log
ls -la /tmp/game_shot.png 2>&1
