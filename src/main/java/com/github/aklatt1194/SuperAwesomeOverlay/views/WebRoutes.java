package com.github.aklatt1194.SuperAwesomeOverlay.views;

import static spark.Spark.*;
import spark.ModelAndView;
import spark.template.freemarker.FreeMarkerEngine;

public class WebRoutes {
    public WebRoutes() {
      setPort(80);
      
      externalStaticFileLocation("src/main/resources/static");
      
      get("/", (req, res) -> {
        return new ModelAndView(null, "landing.ftl");
      }, new FreeMarkerEngine());
      
      get("/network", (req, res) -> {
        return new ModelAndView(null, "network.ftl");
      }, new FreeMarkerEngine());
      
      get("/metrics", (req, res) -> {
        return new ModelAndView(null, "metrics.ftl");
      }, new FreeMarkerEngine());
      
      get("/chat", (req, res) -> {
        return new ModelAndView(null, "chat.ftl");
      }, new FreeMarkerEngine());
    }
}