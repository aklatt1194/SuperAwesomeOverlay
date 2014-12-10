#!/bin/bash
kill -9 `cat scripts/overlay.pid`
nohup mvn exec:java -Dexec.mainClass="com.github.aklatt1194.SuperAwesomeOverlay.Overlay" > scripts/output.log &
echo $! > scripts/overlay.pid
