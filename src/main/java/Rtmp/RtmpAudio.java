package Rtmp;

import Decoder.AudioStreamDecoder;
import User.Receive;
import User.ReceiveGroup;
import Util.MsgType;

import java.util.List;

public class RtmpAudio extends Amf{

    public void setAudioData(byte[] message,String path,int timestamp,AudioStreamDecoder audioStreamDecoder) {
//        audioStreamDecoder.decode(message);

        List<Receive> list = ReceiveGroup.getChannel(path);
        if (list != null) {
            for (Receive receive : list) {
                if (receive.ready && receive.keyframe) {
                    if (!receive.playing) {
                        continue;
                    }
                    RtmpResponse.sendData2(message, MsgType.MSG_AUDIO, 1337, receive.receive, timestamp);
                }
            }
        }
    }
}
