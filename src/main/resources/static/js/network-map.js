var SAO = SAO || {};

SAO.networkMap = {
  init: function() {
    var self = this;
    var color = d3.scale.ordinal().range(['#777', '#797979', '#888', '#898989', '#999', '#9a9a9a', '#aaaaaa', 'bababa']);

    this.projection = d3.geo.mercator()
      .translate([570, 480])
      .scale(175);

    this.path = d3.geo.path().projection(this.projection);

    var vis = d3.select("#map").append("svg")
      .attr("width", 1140)
      .attr("height", 700);

    var mapVis = vis.append("g");
    this.nodeVis = vis.append("g");

    var drawMap = function(error, data) {
      var countries = topojson.object(data, data.objects.countries).geometries,
        neighbors = topojson.neighbors(data, countries),
        i = -1,
        n = countries.length;

      mapVis.selectAll(".country").data(countries)
        .enter()
        .insert("path")
        .attr("class", "country")
        .attr("title", function(d, i) {
          return d.name;
        })
        .attr("d", self.path)
        .style("fill", function(d, i) {
          return color(d.color = d3.max(neighbors[i], function(n) {
            return countries[n].color;
          }) + 1 | 0);
        });

      SAO.knownNodes;
    };

    d3.json("/json/world-110m2.json", drawMap);
  },

  drawNodes: function(nodes) {
    var self = this;

    this.nodeVis.selectAll(".node").data(nodes)
    .enter()
    .append("svg:circle")
    .attr("class", "node")
    .attr("cx", function(d) {
      return self.projection([d.lon, d.lat])[0];
    })
    .attr("cy", function(d) {
      return self.projection([d.lon, d.lat])[1];
    })
    .attr("r", function(d) {
      return d.hostname === window.location.host ? 10 : 5;
    })
    .attr("fill", "#F00");
  }
};