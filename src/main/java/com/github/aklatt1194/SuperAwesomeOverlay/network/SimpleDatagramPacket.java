package com.github.aklatt1194.SuperAwesomeOverlay.network;

import java.net.InetAddress;
import java.nio.ByteBuffer;

public class SimpleDatagramPacket {
    private static final int HEADER_LENGTH = 12;
    
    private InetAddress src, dst;
    private int srcPort, dstPort;
    private int length;
    private byte[] payload;
    
    public SimpleDatagramPacket(InetAddress src, InetAddress dst, int srcPort, int dstPort, byte[] payload) {
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
        
        buf.putInt(srcPort);
        buf.putInt(dstPort);
        buf.putInt(length);
        buf.put(payload);
        
        buf.flip();
        
        return buf;
    }
    
    protected static SimpleDatagramPacket createFromBuffer(ByteBuffer buf, InetAddress src, InetAddress dst) {
        if (buf.remaining() < 12)
            // there isn't a complete header, this must be a partial read
            return null;
        
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
        
        return new SimpleDatagramPacket(src, dst, srcPort, dstPort, payload);
    }
}
