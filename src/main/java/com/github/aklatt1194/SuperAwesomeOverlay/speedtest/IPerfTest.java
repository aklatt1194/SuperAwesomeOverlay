package com.github.aklatt1194.SuperAwesomeOverlay.speedtest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.aklatt1194.SuperAwesomeOverlay.models.MetricsDatabaseManager;
import com.github.aklatt1194.SuperAwesomeOverlay.models.OverlayRoutingModel;

public class IPerfTest implements Runnable {
    public static final int TEST_INTERVAL = 10 * 60 * 1000;
    public static final String[] COMMAND = { "iperf", "-y", "C", "-t", "5", "-p", "4545", "-c" };

    public Map<InetAddress, Long> lastTest;
    private OverlayRoutingModel model;
    private MetricsDatabaseManager db;

    public IPerfTest(OverlayRoutingModel model, MetricsDatabaseManager db) {
        this.model = model;
        this.db = db;

        lastTest = new HashMap<>();

        new Thread(this).start();
    }

    @Override
    public void run() {
        while (true) {
            InetAddress target = null;

            long earliestTest = Long.MAX_VALUE;
            for (InetAddress addr : model.getKnownNeighbors()) {
                Long test = lastTest.get(addr);
                if (test == null) {
                    target = addr;
                    break;
                }

                if (test < earliestTest) {
                    target = addr;
                    earliestTest = test;
                }
            }

            if (target != null) {
                List<String> command = new ArrayList<>(Arrays.asList(COMMAND));
                command.add(target.getHostAddress());

                ProcessBuilder proBuilder = new ProcessBuilder(command);
                Process process = null;
                try {
                    process = proBuilder.start();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }

                while (true) {
                    try {
                        process.waitFor();
                        break;
                    } catch (InterruptedException e) {
                        continue;
                    }
                }

                InputStream is = process.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);

                Double upstreamBandwidth = null;
                try {
                    String result = br.readLine();
                    if (result != null) {
                        String[] tokens = result.split(",");
                        upstreamBandwidth = Double.parseDouble(tokens[tokens.length - 1]);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                } finally {
                    try {
                        is.close();
                        isr.close();
                        br.close();
                    } catch (IOException e) {
                    }
                }

                if (upstreamBandwidth != null) {
                    long now = System.currentTimeMillis();
                    lastTest.put(target, now);
                    db.addThroughputData(target.getHostAddress(), now, upstreamBandwidth);
                }
            }

            try {
                Thread.sleep(TEST_INTERVAL);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
