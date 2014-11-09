package com.github.aklatt1194.SuperAwesomeOverlay;

import org.apache.log4j.BasicConfigurator;

import com.github.aklatt1194.SuperAwesomeOverlay.views.WebRoutes;

public class Overlay {
    public static final String[] NODES = {
            "ec2-54-171-49-149.eu-west-1.compute.amazonaws.com",
            "ec2-54-65-17-109.ap-northeast-1.compute.amazonaws.com",
            "ec2-54-173-133-146.compute-1.amazonaws.com" };

    public static void main(String[] args) {
        new WebRoutes();
        BasicConfigurator.configure();
    }
}
