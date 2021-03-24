package Rtmp;

import AMF.AMFClass;
import AMF.AMFUtil;
import Util.Common;
import Util.MsgType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import lombok.Getter;
import lombok.Setter;

import java.util.Arrays;

public class RtmpChunkSize extends Amf {


    @Setter
    @Getter
    private int chunkSize;

    public void setChunSize(byte[] messageData) {
         setAmfClass(messageData);
        if (amfClass.pos + 4 > amfClass.message.length) {
            System.err.println("数据不足");
            return;
        }
        chunkSize = Common.byteToInt(Arrays.copyOfRange(amfClass.message, amfClass.pos, amfClass.pos + 4));
    }

    // 服务器 告诉客户端，自己是使用 多大的 chunk 大小
    public void responseChunkSize(ChannelHandlerContext ctx,int chunkLength) {
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
        byte[] data = AMFUtil.writeNumber(chunkLength);
        byteBuf.writeBytes(data);
        int length2 = byteBuf.readableBytes();
        byte[] beginData2 = new byte[length2];
        byteBuf.readBytes(beginData2);
        RtmpResponse.sendData(beginData2, MsgType.MSG_CHUNK_SIZE, 0, ctx, 0,chunkSize);
    }
}
