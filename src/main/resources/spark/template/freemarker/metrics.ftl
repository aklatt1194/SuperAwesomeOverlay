<#include "header.ftl">

<div class="container">
  <div id="latency-chart"></div>
  <div id="throughput-chart"></div>
</div> <!-- /container -->

    <script src="js/jquery.min.js"></script>
    <script src="js/bootstrap.min.js"></script>
    <script src="js/highstock.js"></script>
    <script src="js/networkoverlay.js"></script>
    <script src="js/network-metrics.js"></script>
    <script>
      SAO.setup();
      SAO.metrics.latencyChart();
    </script>
  </body>
</html>