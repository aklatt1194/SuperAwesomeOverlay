var SAO = SAO || {};

SAO.networkMap = {
  init: function() {
    var color = d3.scale.ordinal().range(['#777', '#797979', '#888', '#898989', '#999', '#9a9a9a', '#aaaaaa', 'bababa']),
      aspectRatio = 0.6,
      zoomFactor = 0.15,
      width = $('#map').width(),
      height = width * aspectRatio,
      projection, path, arc, vis, mapVis, linkVis, nodeVis, socket;

    projection = d3.geo.mercator()
      .translate([width / 2, 2 * height / 3])
      .scale(width * zoomFactor);

    path = d3.geo.path().projection(projection);
    arc = d3.geo.greatArc().precision(3);

    vis = d3.select('#map').append('svg')
      .style('width', width + 'px')
      .style('height', height + 'px');

    mapVis = vis.append('g');
    linkVis = vis.append('g');
    nodeVis = vis.append('g');

    var drawMap = function(error, data) {
      var countries = topojson.object(data, data.objects.countries).geometries,
        neighbors = topojson.neighbors(data, countries),
        i = -1,
        n = countries.length;

      mapVis.selectAll('.country').data(countries)
        .enter()
        .insert('path')
        .attr('class', 'country')
        .attr('title', function(d, i) {
          return d.name;
        })
        .attr('d', path)
        .style('fill', function(d, i) {
          return color(d.color = d3.max(neighbors[i], function(n) {
            return countries[n].color;
          }) + 1 | 0);
        });
    };

    var drawNetwork = function(data) {
      var root = JSON.parse(data.data),
        tree, nodes, links, nodeSel, linkSel;

      tree = d3.layout.tree();
      nodes = tree.nodes(root);
      links = tree.links(nodes);

      nodeSel = nodeVis.selectAll('.node')
        .data(nodes, function(d) {
          return 'node' + d.ip;
        });

      nodeSel.enter()
        .append('svg:circle')
        .attr('class', function(d) {
          return d === root ? 'node self' : 'node';
        })
        .attr('cx', function(d) {
          return projection([d.lon, d.lat])[0];
        })
        .attr('cy', function(d) {
          return projection([d.lon, d.lat])[1];
        })
        .attr('r', function(d) {
          return d === root ? 7 : 5;
        })
        .on('click', function(d) {
          window.location.href = 'http://' + d.hostname + '/network';
        })
        .sort(function(a, b) {
          if (a === root) {
            return 1;
          } else if (b === root) {
            return -1;
          } else {
            return 0;
          }
        });

      nodeSel.exit().remove();

      linkSel = linkVis.selectAll('.link')
        .data(links, function(d) {
          return 'link' + d.source.ip + '<->' + d.target.ip;
        });

      linkSel.enter()
        .append('svg:path')
        .attr('class', 'link')
        .style('stroke-width', 5)
        .style('stroke', 'blue')
        .style('fill', 'none')
        .style('opacity', 0.5)
        .attr('d', function(d) {
          return path(arc({
            source: [d.source.lon, d.source.lat],
            target: [d.target.lon, d.target.lat]
          }));
        });

      linkSel.exit().remove();
    };

    var resize = function() {
      var newWidth = $('#map').width();

      if (newWidth != width) {
        width = newWidth;
        height = aspectRatio * width;

        projection.translate([width / 2, 2 * height / 3])
          .scale(zoomFactor * width);

        vis.style('width', width + 'px')
          .style('height', height + 'px');

        vis.selectAll('.country').attr('d', path);
        vis.selectAll('.node').attr('cx', function(d) {
            return projection([d.lon, d.lat])[0];
          })
          .attr('cy', function(d) {
            return projection([d.lon, d.lat])[1];
          });

        vis.selectAll('.link').attr('d', function(d) {
          return path(arc({
            source: [d.source.lon, d.source.lat],
            target: [d.target.lon, d.target.lat]
          }));
        });
      }
    }

    socket = new WebSocket('ws://' + window.location.host + ':8025/endpoints/topology');
    socket.onmessage = drawNetwork;

    d3.json('/json/world-110m2.json', drawMap);
    d3.select(window).on('resize', resize);
  }
};