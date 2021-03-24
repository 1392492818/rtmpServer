package Rtmp;

import AMF.AMFClass;

public class Amf {
    protected AMFClass amfClass;
    public void setAmfClass(byte[] messageData) {
        amfClass = new AMFClass();
        amfClass.message = messageData;
        amfClass.pos = 0;
    }
}
