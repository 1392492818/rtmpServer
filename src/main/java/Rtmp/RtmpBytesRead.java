package Rtmp;

import Util.Common;

public class RtmpBytesRead extends Amf{
    public void setBytesRead(byte[] message) {
        System.out.println("bytes read");
        setAmfClass(message);
        if (amfClass.pos + 4 > amfClass.message.length) {
            System.out.println("数据不足");
        }
        System.out.println(Common.bytes2hex(amfClass.message));
        byte[] number2 = new byte[4];
        int index2 = 0;
        for (int i = amfClass.pos; i < amfClass.pos + 4; i++) {
            number2[index2] = amfClass.message[i];
            index2++;
        }
        int len = Common.byteToInt(number2);
        System.err.println("len" + len);
    }
}
