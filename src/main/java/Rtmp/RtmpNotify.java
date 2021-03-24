package Rtmp;

import AMF.AMFUtil;
import User.Publish;
import User.PublishGroup;

import java.util.Map;

public class RtmpNotify  extends Amf{
    public void setNotify(byte[] messageData,String path) {
        setAmfClass(messageData);
        String command = AMFUtil.load_amf_string(amfClass);
        System.err.println(command);
        if (command.equals("@setDataFrame")) {
            String type = AMFUtil.load_amf_string(amfClass);
            System.out.println(type);
            Map<String, Object> data = AMFUtil.load_amf_mixedArray(amfClass);
            Publish publish = PublishGroup.getChannel(path);
            if (publish != null) {
                publish.MetaData = data;
            }
        }
    }
}
