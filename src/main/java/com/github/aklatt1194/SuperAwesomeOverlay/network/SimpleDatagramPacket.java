package com.github.aklatt1194.SuperAwesomeOverlay.network;

import java.net.InetAddress;
import java.nio.ByteBuffer;

public class SimpleDatagramPacket {
    public static final int UNITCAST_TYPE = 0;
    public static final int BROADCAST_TYPE = 1;
    
    private static final int HEADER_LENGTH = 20;
    
    private int type; // Could just be a byte, but then the header size isn't as pretty
    private int ttl;
    private InetAddress src, dst;
    private int srcPort, dstPort;
    private int length;
    private byte[] payload;
    
    public SimpleDatagramPacket(InetAddress src, InetAddress dst, int srcPort, int dstPort, byte[] payload) {
        this(src, dst, 0, 1, srcPort, dstPort, payload);
    }
    
    public SimpleDatagramPacket(InetAddress src, InetAddress dst, int type, int ttl, int srcPort, int dstPort, byte[] payload) {
        this.type = type;
        this.ttl = ttl;
        this.src = src;
        this.dst = dst;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
        this.payload = payload;
        length = HEADER_LENGTH + payload.length;
    }
    
    public InetAddress getSource() {
        return src;
    }
    
    public InetAddress getDestination() {
        return dst;
    }
    
    public int getType() {
        return type;
    }
    
    public int getTTL() {
        return ttl;
    }
    
    public int getSourcePort() {
        return srcPort;
    }
    
    public int getDestinationPort() {
        return dstPort;
    }
    
    public byte[] getPayload() {
        return payload;
    }
    
    
    public ByteBuffer getRawPacket() {
        ByteBuffer buf = ByteBuffer.allocate(length);
        
        buf.putInt(type);
        buf.putInt(ttl);
        buf.putInt(srcPort);
        buf.putInt(dstPort);
        buf.putInt(length);
        buf.put(payload);
        
        buf.flip();
        
        return buf;
    }
    
    protected static SimpleDatagramPacket createFromBuffer(ByteBuffer buf, InetAddress src, InetAddress dst) {
        if (buf.remaining() < HEADER_LENGTH)
            // there isn't a complete header, this must be a partial read
            return null;
        
        int type = buf.getInt();
        int ttl = buf.getInt();
        int srcPort = buf.getInt();
        int dstPort = buf.getInt();
        int length = buf.getInt();
        
        if (buf.remaining() < length - HEADER_LENGTH) {
            // there isn't a complete payload, this must be a partial read
            buf.position(buf.position() - HEADER_LENGTH);
            return null;
        }
        
        byte[] payload = new byte[length - HEADER_LENGTH];
        buf.get(payload);
        
        return new SimpleDatagramPacket(src, dst, type, ttl, srcPort, dstPort, payload);
    }
}
