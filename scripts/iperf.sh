#!/bin/sh
kill -9 `cat scripts/iperf.pid`
nohup iperf -s -p 4545 -o scripts/iperf.log &
echo $! > scripts/iperf.pid
