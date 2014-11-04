package com.github.aklatt1194.SuperAwesomeOverlay.views;

import static spark.Spark.*;

public class Zebra {
    public Zebra() {
	get("/hello", (req, res) -> "Hello World");
    }
}
