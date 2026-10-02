#!/bin/bash
cd /c/Users/brt.mabbass/Desktop/Projects/Minecraft
pkill -f com.example.Main 2>/dev/null; sleep 1
mvn -o -q package -DskipTests 2>&1 | grep ERROR | head -3
(mvn -o exec:java -Dtest.controller=true -Dexec.mainClass=com.example.Main > "$CLAUDE_JOB_DIR/tmp/gm.log" 2>&1 &)
for i in $(seq 1 40); do sleep 1; P=$(grep -ao "127.0.0.1:[0-9]*" "$CLAUDE_JOB_DIR/tmp/gm.log" | head -1); [ -n "$P" ] && break; done
echo "http://$P" > "$CLAUDE_JOB_DIR/tmp/url.txt"; sleep 3
U=http://$P
curl -s "$U/newTestWorld?type=flat" >/dev/null; sleep 4; curl -s "$U/setMobSpawning?enabled=false" >/dev/null
echo $U
