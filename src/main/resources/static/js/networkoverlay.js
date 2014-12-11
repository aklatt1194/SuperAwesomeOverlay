var SAO = SAO || {};

SAO.setup = function() {
  // set the current tab to active
  $('a[href="' + window.location.pathname + '"]').parent().addClass('active');

  $.get('endpoints/known_nodes', function(data) {
    SAO.setNodeNavigation(data);
  });
};

SAO.setNodeNavigation = function(nodes) {
  $('.fa-spinner').remove()

  for (var i = 0; i < nodes.length; i++) {
    var location_string = nodes[i].country;
    if (nodes[i].region_name)
      location_string = nodes[i].region_name + ', ' + location_string;
    if (nodes[i].city_name)
      location_string = nodes[i].city_name + ', ' + location_string;

    if (nodes[i].self) {
      $('a.current-node').html(location_string);
    } else {
      $('.dropdown-menu').append($('<li></li>').append($('<a></a>').html(location_string).attr('href', 'http://' + nodes[i].hostname + window.location.pathname + window.location.hash)));
    }
  }
};

SAO.chat = function() {
  var socket = new WebSocket('ws://' + window.location.host + ':8025/endpoints/chat'),
    chatWindow = $('#chat-window');

  socket.onmessage = function(data) {
    var msg = JSON.parse(data.data);

    var time = $('<div></div>').html(new Date(msg.timestamp).toLocaleTimeString().replace(/:[0-9]* /g, '')).addClass('timestamp');
    var name = $('<div></div>').html(msg.user + ':').addClass('username');
    var message = $('<div></div>').html(msg.message).css("color", msg.color).addClass('message');

    chatWindow.append(time, name, message);
    chatWindow.scrollTop(chatWindow[0].scrollHeight);
  };

  socket.onclose = function() {
    chatWindow.append($('<h3></h3>').html('disconnected from server'));
    chatWindow.scrollTop(chatWindow[0].scrollHeight);
  };

  $('#send-message').click(function() {
    socket.send(JSON.stringify({
      message: $('#message').val(),
      user: $('#username').val(),
      color: $("#color-box").css("background-color"),
      timestamp: new Date().getTime()
    }));
  });

  $("#message-color").ColorPickerSliders({
    color: '#1F3D5C',
    size: 'sm',
    placement: 'top',
    swatches: false,
    onchange: function(container, color) {
      $("#color-box").css("background-color", color.tiny.toRgbString());
    }
  });
};