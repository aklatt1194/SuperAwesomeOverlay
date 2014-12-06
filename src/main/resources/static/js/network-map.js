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
    this.linkVis = vis.append("g");
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
    $.get("/endpoints/network_topology", (function(self) {
      return function(root) {
        self.drawNetwork.call(self, root);
      }
    })(this));
  },

  drawNetwork: function(root) {
    var self = this,
      tree,
      nodes,
      links,
      path = d3.geo.path().projection(this.projection),
      arc = d3.geo.greatArc().precision(3);

    tree = d3.layout.tree();
    nodes = tree.nodes(root);
    links = tree.links(nodes);

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
      .attr("fill", "#F00")
      .on("click", function (d) {
        console.log(d);
        window.location.href = "http://" + d.hostname + "/network";
      });

    this.linkVis.selectAll(".link").data(links)
      .enter()
      .append("svg:path")
      .attr("class", "link")
      .style("stroke-width", 5)
      .style("stroke", "blue")
      .style("fill", "none")
      .style("opacity", 0.5)
      .attr("d", function(d) {
        return path(arc({
          source: [d.source.lon, d.source.lat],
          target: [d.target.lon, d.target.lat]
        }));
      });
  }
};