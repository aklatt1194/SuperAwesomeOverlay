<#include "header.ftl">

    <div class="container">

      <div class="panel panel-default col-md-8 col-md-offset-2">
        <div id="chat-window" class="panel-body">
        </div>
      </div>
      
      <div class="row">
        <div class="col-md-offset-2 col-md-4">
          <div class="input-group">
            <span class="input-group-addon">Username</span>
            <input id="username" type="text" class="form-control" maxlength="8" value="Unknown">
          </div>
        </div>

        <div class="col-md-offset-1 col-md-3">
          <div class="input-group" id="message-color">
            <input id="color-box" type="text" class="form-control" disabled="disabled">
            <span class="input-group-addon">Message Color</span>
          </div>
        </div>
      </div>
    
      <div class="row">
        <div class="col-md-8 col-md-offset-2">
          <div class="input-group">
            <span class="input-group-addon">Message</span>
            <input id="message" type="text" class="form-control">
            <span class="input-group-btn">
              <button id="send-message" class="btn btn-default" type="button">Send</button>
            </span>
          </div>
        </div>
      </div>
    </div>

    <script src="js/jquery.min.js"></script>
    <script src="js/bootstrap.min.js"></script>
    <script src="js/networkoverlay.js"></script>
    <script src="js/tinycolor.js"></script>
    <script src="js/bootstrap.colorpickersliders.min.js"></script>
    <script>
      SAO.setup();
      SAO.chat();
    </script>
  </body>
</html>