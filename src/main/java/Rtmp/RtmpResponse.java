package Rtmp;

import Util.Common;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

public class RtmpResponse {

     // sendData 是 chunk设置了长度之后的返回，  发送端 跟 接收方的chunksize 不一致，需要重新修改，暂时还没修改
    private static  ByteBuf streamByteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
    private static int sendChunkLength = Common.DEFAULT_CHUNK_MESSAGE_LENGTH;
    public static void sendData(byte[] chunkData, byte msgType, int streamId, ChannelHandlerContext ctx, int timestamp,int chunkLength) {
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
        byte flags;
        if (streamId == Common.STREAM_ID) {
            flags = (4 & 0x3f) | (0 << 6);
        } else {
            flags = (3 & 0x3f) | (0 << 6);
        }
        //  byte[] timestamp = {0x00,0x00,0x00};
        int msg_len = chunkData.length;
        byte[] msgLength = Common.reverseArray(Common.intToByte24(msg_len));
        byte[] streamData = Common.intToByte(streamId);
        byteBuf.writeByte(flags);
        byteBuf.writeBytes(Common.reverseArray(Common.intToByte24(timestamp)));
        byteBuf.writeBytes(msgLength);
        byteBuf.writeByte(msgType);
        byteBuf.writeBytes(streamData);
        int pos = 0;
        while (pos < chunkData.length) {
            if (byteBuf.writableBytes() < chunkData.length) {
                byteBuf.ensureWritable(chunkData.length);
            }
            if (chunkData.length - pos < chunkLength) {
                for (int i = pos; i < chunkData.length; i++) {
                    byteBuf.writeByte(chunkData[i]);
                }
            } else {
                if (byteBuf.writableBytes() < pos + chunkLength) {
                    byteBuf.ensureWritable(pos + chunkLength);
                }
                for (int i = pos; i < pos + chunkLength; i++) {
                    byteBuf.writeByte(chunkData[i]);
                }
                if (streamId == Common.STREAM_ID) {
                    byteBuf.writeByte((byte) ((4 & 0x3f) | (3 << 6)));
                } else {
                    byteBuf.writeByte((byte) ((3 & 0x3f) | (3 << 6)));
                }
            }
            pos += chunkLength;
        }
        int sendLength = byteBuf.readableBytes();
        byte[] sendData = new byte[sendLength];
        byteBuf.readBytes(sendData);
        ctx.writeAndFlush(Unpooled.copiedBuffer(sendData));
        byteBuf.release();
    }



    /**
     * 同意数据发送
     *
     * @param chunkData
     * @param msgType
     * @param streamId
     * @param ctx
     */
    public static void sendData2(byte[] chunkData, byte msgType, int streamId, ChannelHandlerContext ctx, int timestamp) {
        byte flags;
        if (streamId == Common.STREAM_ID) {
            flags = (4 & 0x3f) | (0 << 6);
        } else {
            flags = (3 & 0x3f) | (0 << 6);
        }
        //  byte[] timestamp = {0x00,0x00,0x00};
        int msg_len = chunkData.length;
        byte[] msgLength = Common.reverseArray(Common.intToByte24(msg_len));
        byte[] streamData = Common.intToByte(streamId);
        streamByteBuf.writeByte(flags);
        //  System.out.println(Common.bytes2hex(Common.reverseArray(Common.intToByte24(timestamp))));
        streamByteBuf.writeBytes(Common.reverseArray(Common.intToByte24(timestamp)));
        streamByteBuf.writeBytes(msgLength);
        streamByteBuf.writeByte(msgType);
        streamByteBuf.writeBytes(streamData);
        int pos = 0;

        while (pos < chunkData.length) {
            if (streamByteBuf.writableBytes() < chunkData.length) {
                streamByteBuf.ensureWritable(chunkData.length);
            }
            if (chunkData.length - pos < sendChunkLength) {
                for (int i = pos; i < chunkData.length; i++) {
                    streamByteBuf.writeByte(chunkData[i]);
                }
            } else {
                if (streamByteBuf.writableBytes() < pos + sendChunkLength) {
                    streamByteBuf.ensureWritable(pos + sendChunkLength);
                }
                for (int i = pos; i < pos + sendChunkLength; i++) {
                    streamByteBuf.writeByte(chunkData[i]);
                }
                if (pos + sendChunkLength != chunkData.length) {
                    if (streamId == Common.STREAM_ID) {
                        streamByteBuf.writeByte((byte) ((4 & 0x3f) | (3 << 6)));
                    } else {
                        streamByteBuf.writeByte((byte) ((3 & 0x3f) | (3 << 6)));
                    }
                }
            }
            pos += sendChunkLength;
        }
        sendClient(ctx);
    }

    private static void sendClient(ChannelHandlerContext ctx) {
        if (streamByteBuf.readableBytes() > 0) {
            int length = streamByteBuf.readableBytes();
            //if(length > 4096) length = 4096;
            byte[] sendData = new byte[length];
            streamByteBuf.readBytes(sendData);
            ctx.fireChannelRead(sendData);
        }
    }
}
