<#include "header.ftl">

    <div class="container">
      <div class="panel panel-default col-md-8 col-md-offset-2">
        <div id="chat-window" class="panel-body">
        </div>
      </div>
      
      <div class="input-group col-md-offset-2 col-md-3">
        <span class="input-group-addon">Username</span>
        <input id="username" type="text" class="form-control">
      </div>

      <div class="input-group col-md-8 col-md-offset-2">
        <span class="input-group-addon">Message</span>
        <input id="message" type="text" class="form-control">
        <span class="input-group-btn">
          <button id="send-message" class="btn btn-default" type="button">Send</button>
        </span>
      </div>
    </div>

    <script src="js/jquery.min.js"></script>
    <script src="js/bootstrap.min.js"></script>
    <script src="js/networkoverlay.js"></script>
    <script>
      SAO.setup();
      SAO.chat();
    </script>
  </body>
</html>