package Rtmp;

public class RtmpHandshake {
    public byte[] C0 = {0x03};
    public byte[] S0 = {0x03};
    public byte[] C1 = new byte[1536];
    public byte[] S1 = new byte[1536];
    public byte[] C2 = new byte[1536];
    public byte[] S2 = new byte[1536];
}
