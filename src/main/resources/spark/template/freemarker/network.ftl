<#include "header.ftl">

    <div class="container">
      <div id="map" class="center-block"></div>
    </div>

    <script src="js/jquery.min.js"></script>
    <script src="js/bootstrap.min.js"></script>
    <script src="js/networkoverlay.js"></script>
    <script src="js/network-map.js"></script>
    <script src="js/d3.v3.min.js"></script>
    <script src="js/topojson.js"></script>
    <script>
      SAO.setup();
      SAO.networkMap.init();
    </script>
  </body>
</html>