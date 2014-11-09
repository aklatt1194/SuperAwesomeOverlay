var SAO = SAO || {}

SAO.setup = function() {
  // set the current tab to active
  $('a[href="' + window.location.pathname + '"]').parent().addClass('active');

  $.get('endpoints/known_nodes', function(data) {
    SAO.known_nodes = data;
    SAO.set_node_navigation(data);
  });
}

SAO.set_node_navigation = function(nodes) {
  for (var i = 0; i < nodes.length; i++) {
    var location_string = nodes[i].country;
    if (nodes[i].region_name)
      location_string = nodes[i].region_name + ', ' + location_string;
    if (nodes[i].city_name)
      location_string = nodes[i].city_name + ', ' + location_string;

    if (nodes[i].hostname === window.location.host) {
      $('a.current-node').html(location_string);
    } else {
      $('.dropdown-menu').append($('<li></li>').append($('<a></a>').html(location_string).attr('href', 'http://' + nodes[i].hostname)));
    }
  }
}