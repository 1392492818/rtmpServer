package Rtmp;

public class RtmpPing extends Amf {
    public void setPing(byte[] message) {
        setAmfClass(message);
        System.out.println("ping === " + message.length);
    }
}
