<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <title>NetworkOverlay</title>
    <link rel="stylesheet" href="css/bootstrap.min.css">
    <link rel="stylesheet" href="css/bootstrap-theme.min.css">
    <link rel="stylesheet" href="css/font-awesome.min.css">
    <link rel="stylesheet" href="css/networkoverlay.css">
    <link rel="stylesheet" href="css/bootstrap.colorpickersliders.min.css">
  </head>

  <body>
    <!-- Fixed navbar -->
    <div class="navbar navbar-default navbar-fixed-top" role="navigation">
      <div class="container">
        <div class="navbar-header">
          <button type="button" class="navbar-toggle" data-toggle="collapse" data-target=".navbar-collapse">
            <span class="sr-only">Toggle navigation</span>
            <span class="icon-bar"></span>
            <span class="icon-bar"></span>
            <span class="icon-bar"></span>
          </button>
          <a class="navbar-brand" href="/">NetworkOverlay</a>
        </div>
        <div class="navbar-collapse collapse">
          <ul class="nav navbar-nav">
            <li><a href="/network">Network</a></li>
            <li><a href="/metrics">Metrics</a></li>
            <li><a href="/chat">Chat</a></li>
          </ul>
          <ul class="nav navbar-nav navbar-right">
            <li><a class="current-node"><i class="fa fa-spinner fa-spin"></i></a></li>
            <li class="dropdown">
              <a href="#" class="dropdown-toggle" data-toggle="dropdown">Switch Node <span class="caret"></span></a>
              <ul class="dropdown-menu" role="menu">
                <li><i class="fa fa-spinner fa-spin"></i></li>
              </ul>
            </li>
          </ul>
        </div><!--/.nav-collapse -->
      </div>
    </div>