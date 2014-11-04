package com.github.aklatt1194.SuperAwesomeOverlay.views;

import static spark.Spark.*;

public class Zebra {
    public Zebra() {
      setPort(80);
      get("/", (req, res) -> "Hello World");
    }
}
