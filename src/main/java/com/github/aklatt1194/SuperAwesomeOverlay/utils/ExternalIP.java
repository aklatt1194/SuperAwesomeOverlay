package com.github.aklatt1194.SuperAwesomeOverlay.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import spark.utils.IOUtils;

public class ExternalIP {
	public static InetAddress getExternalAddress() throws MalformedURLException, IOException {
		URLConnection connection = new URL("http://wtfismyip.com/text").openConnection();
		connection.setRequestProperty("Accept-Charset", "UTF-8");
		InputStream response = connection.getInputStream();
		
		return InetAddress.getByName(IOUtils.toString(response));
	}
}
