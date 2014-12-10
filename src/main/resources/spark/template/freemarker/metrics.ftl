<#include "header.ftl">

<div class="container">

  <ul class="nav nav-tabs" role="tablist">
    <li class="active"><a href="#latency" role="tab">Latency</a></li>
    <li><a href="#throughput" role="tab">Throughput</a></li>
  </ul>

  <div class="tab-content">
    <div class="tab-pane active" id="latency">
      <div id="latency-chart"></div>
    </div>
    <div class="tab-pane" id="throughput">
      <div id="throughput-chart"></div>
    </div>
  </div>

</div> <!-- /container -->

    <script src="js/jquery.min.js"></script>
    <script src="js/bootstrap.min.js"></script>
    <script src="js/highstock.js"></script>
    <script src="js/networkoverlay.js"></script>
    <script src="js/network-metrics.js"></script>
    <script>
      SAO.setup();
      SAO.metrics.init();
    </script>
  </body>
</html>