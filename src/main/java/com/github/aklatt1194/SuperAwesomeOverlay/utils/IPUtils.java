package com.github.aklatt1194.SuperAwesomeOverlay.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import spark.utils.IOUtils;

public class IPUtils {
    public static InetAddress getExternalAddress()
            throws MalformedURLException, IOException {
        URLConnection connection = new URL("http://wtfismyip.com/text")
                .openConnection();
        connection.setRequestProperty("Accept-Charset", "UTF-8");
        InputStream response = connection.getInputStream();
        
        return InetAddress.getByName(IOUtils.toString(response).trim());
    }
    
    public static int compareIPs(InetAddress one, InetAddress two) {
        byte[] oneBytes = one.getAddress();
        byte[] twoBytes = two.getAddress();
        
        // Longer address is greater
        if (oneBytes.length != twoBytes.length)
            return oneBytes.length - twoBytes.length;
        
        // Otherwise compare byte by byte
        for (int i = 0; i < oneBytes.length; i++) {
            if (oneBytes[i] != twoBytes[i])
                return oneBytes[i] = twoBytes[i];
        }
        
        return 0;
    }
}
