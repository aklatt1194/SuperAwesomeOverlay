package com.github.aklatt1194.SuperAwesomeOverlay.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

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
                
        // Longer address is greater -- TODO: shouldn't they always be the same length?
        if (oneBytes.length != twoBytes.length)
            return oneBytes.length - twoBytes.length;
        
        // Otherwise compare byte by byte
        for (int i = 0; i < oneBytes.length; i++) {
            if (oneBytes[i] != twoBytes[i])
                return oneBytes[i] - twoBytes[i];
        }
        
        return 0;
    }
    
    public static void serializeIPAddr(InetAddress addr, ByteBuffer buf) {
        byte[] bytes = addr.getAddress();
        buf.putInt(bytes.length);
        buf.put(bytes);
    }
    
    public static InetAddress deserializeIPAddr(ByteBuffer buf) {
        int len = buf.getInt();
        byte[] bytes = new byte[len];
        
        for (int i = 0; i < len; i++) {
            bytes[i] = buf.get();
        }
        
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
