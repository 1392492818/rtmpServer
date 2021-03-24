package Decoder;

import Rtmp.*;
import User.Receive;
import User.ReceiveGroup;
import Util.Common;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.*;

public class RtmpDecoder extends ByteToMessageDecoder {

    //  chunk message 数据
    private ByteBuf byteBuf = null;

    //握手数据
    private RtmpHandshake rtmpHandshake = new RtmpHandshake();

    private String path = null;

    AudioStreamDecoder audioStreamDecoder = new AudioStreamDecoder();

    private RtmpPacket rtmpPacket = null;

    private int chunkLength = Common.DEFAULT_CHUNK_MESSAGE_LENGTH;

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byteBuf = in;
        if (!rtmpHandshake.isHandshake) { //rtmp 握手认证
            rtmpHandshake.handShake(ctx,in);
        } else {
            handChunkMessage(ctx);
        }
    }

//      +-------+     +--------------+----------------+
//              | Chunk |  =  | Chunk Header |   Chunk Data   |
//      +-------+     +--------------+----------------+
//
//      +--------------+     +-------------+----------------+-------------------+
//              | Chunk Header |  =  | Basic header| Message Header |Extended Timestamp |
//      +--------------+     +-------------+----------------+-------------------+
//
    /**
     * 解析chunkMessage 数据
     *
     * @param ctx
     */
    private void handChunkMessage(ChannelHandlerContext ctx) {
        if(rtmpPacket != null && rtmpPacket.isLackMessage()) {
            rtmpPacket.lackMessage(byteBuf);
        }
        else if(rtmpPacket != null){
            rtmpPacket.reset(byteBuf);
        }
        else {
            rtmpPacket = new RtmpPacket(ctx,byteBuf);
        }
        if(rtmpPacket.isLackMessage()){
            return;
        }

        if(!rtmpPacket.isSubpackage()) {
            switch (rtmpPacket.getMessageType()) {
                case ChunkSize:
                    RtmpChunkSize rtmpChunkSize = new RtmpChunkSize();
                    rtmpChunkSize.setChunSize(rtmpPacket.getMessageData());
                    rtmpChunkSize.responseChunkSize(ctx,Common.DEFAULT_CHUNK_MESSAGE_LENGTH);
                    rtmpPacket.setChunkLength(rtmpChunkSize.getChunkSize());
//                    RtmpResponse.chunkLength = rtmpChunkSize.getChunkSize();
                    chunkLength = rtmpChunkSize.getChunkSize();
                    break;
                case BYTES_READ:
                    RtmpBytesRead rtmpBytesRead = new RtmpBytesRead();
                    rtmpBytesRead.setBytesRead(rtmpPacket.getMessageData());
                    break;
                case PING:
                    RtmpPing rtmpPing = new RtmpPing();
                    rtmpPing.setPing(rtmpPacket.getMessageData());
                    break;
                case SERVER_WINDOW:
                    RtmpServerWindow rtmpServerWindow = new RtmpServerWindow();
                    rtmpServerWindow.setRtmpServerWindow(rtmpPacket.getMessageData());
                    break;
                case AUDIO:
                    RtmpAudio rtmpAudio = new RtmpAudio();
                    rtmpAudio.setAudioData(rtmpPacket.getMessageData(),this.path,rtmpPacket.getTimestamp(),audioStreamDecoder);
                    break;
                case VIDEO:
                    RtmpVideo rtmpVideo = new RtmpVideo();
                    rtmpVideo.setVideoData(rtmpPacket.getMessageData(),this.path,rtmpPacket.getTimestamp());
                    break;
                case NOTIFY:
                    RtmpNotify rtmpNotify = new RtmpNotify();
                    rtmpNotify.setNotify(rtmpPacket.getMessageData(),this.path);
                    break;
                case Control:
                    RtmpControl rtmpControl = new RtmpControl();
                    rtmpControl.setControl(rtmpPacket.getMessageData(),ctx,chunkLength);
                    System.out.println("strameId === " + rtmpPacket.getStreamId());
                    if(rtmpControl.getMsg().equals("FCPublish") || rtmpControl.getMsg().equals("play"))
                        this.path = rtmpControl.getPath();
                    break;
                case NOT:
                    System.out.println("消息类型还没编写");
                    RtmpNot rtmpNot = new RtmpNot();
                    rtmpNot.setNot(rtmpPacket.getMessageData());
            }
        }


        if(rtmpPacket.getByteBuf().readableBytes() > 0){
            handChunkMessage(ctx);
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.err.println("错误信息");
        System.out.println(cause.getMessage());
        List<Receive> listVideo = ReceiveGroup.getChannel(this.path);
        List<Receive> newListVideo = new ArrayList<Receive>();
        for (int i = 0; i < listVideo.size(); i++) {
            Receive receive = listVideo.get(i);
            if (receive.receive != ctx) {
                newListVideo.add(receive);
            }
        }
        ReceiveGroup.setChannel(this.path, newListVideo);
        super.exceptionCaught(ctx, cause);
    }

}