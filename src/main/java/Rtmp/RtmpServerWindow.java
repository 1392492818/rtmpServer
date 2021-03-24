package Rtmp;

import Util.Common;

import java.util.Arrays;

public class RtmpServerWindow extends Amf{
    public void setRtmpServerWindow(byte[] message) {
        setAmfClass(message);
        if (amfClass.pos + 4 > amfClass.message.length) {
            System.err.println("数据不足");
        }
//      System.err.println(Common.bytes2hex(amfClass.message));
        byte[] number = new byte[4];
        int index = 0;
        for (int i = amfClass.pos; i < amfClass.pos + 4; i++) {
            number[index] = amfClass.message[i];
            index++;
        }
        int windowSize = Common.byteToInt(number);
        System.err.println("window size" + windowSize);
    }
}
