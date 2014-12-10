#!/bin/bash
killall iperf
iperf -s -p 4545 -D > scripts/iperf.log
kill -9 `cat scripts/overlay.pid`
nohup mvn exec:java -Dexec.mainClass="com.github.aklatt1194.SuperAwesomeOverlay.Overlay" > scripts/output.log &
echo $! > scripts/overlay.pid
