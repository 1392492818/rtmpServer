package Rtmp;

import Decoder.VideoStreamDecoder;
import User.Publish;
import User.PublishGroup;
import User.Receive;
import User.ReceiveGroup;
import Util.Common;
import Util.MsgType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.List;

import static Util.Common.FLV_KEY_FRAME;

public class RtmpVideo {

    public void setVideoData(byte[] message,String path,int timestamp) {
        byte flags = message[0];
//        new VideoStreamDecoder(message);

//                System.out.println("=======================");
//                System.out.println(Common.bytes2hex(message));
//                System.out.println("=======================");
        Publish publish = PublishGroup.getChannel(path);

        if (!publish.keyFrame) {
            publish.keyFrame = true;
            publish.keyFrameMessage = message;
        }
        List<Receive> listVideo = ReceiveGroup.getChannel(path);
        if (listVideo != null) {
            for (Receive receive : listVideo) {
                if (!receive.playing) {
                    continue;
                }
                if (receive != null && receive.playing) {
                    if (flags >> 4 == FLV_KEY_FRAME && !receive.ready) {
                        System.out.println("flags ====== " + flags);
                        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
                        byteBuf.writeByte(0x00);
                        byteBuf.writeByte(0x00);
                        byteBuf.writeBytes(Common.intToByte(1337));
                        byte[] control = new byte[byteBuf.readableBytes()];
                        byteBuf.readBytes(control);
                        RtmpResponse.sendData(control, MsgType.MSG_USER_CONTROL, 0, receive.receive, 0,Common.DEFAULT_CHUNK_MESSAGE_LENGTH);
                        receive.ready = true;
                        byteBuf.release();
                    }
                    if (!receive.keyframe) {
                        System.out.println("关键进来了");
                        receive.keyframe = true;
                        RtmpResponse.sendData2(publish.keyFrameMessage, MsgType.MSG_VIDEO, 1337, receive.receive, timestamp);
                    }
                    if (receive.ready && receive.keyframe) {
                        //System.out.println("时间戳" + timestamp);
                       //  System.out.println("video发送数据 ===" + message.length +" 时间戳" + timestamp);
                        RtmpResponse.sendData2(message, MsgType.MSG_VIDEO, 1337, receive.receive, timestamp);
                    }
                }
            }
        }
    }
}
