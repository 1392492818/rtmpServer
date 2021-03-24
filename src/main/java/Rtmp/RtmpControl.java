package Rtmp;

import AMF.AMFClass;
import AMF.AMFUtil;
import User.Publish;
import User.PublishGroup;
import User.Receive;
import User.ReceiveGroup;
import Util.Common;
import Util.MsgType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RtmpControl extends Amf {

    @Getter
    @Setter
    private String msg;
    @Getter
    @Setter
    private double txid;

    private ChannelHandlerContext ctx;

    @Setter
    @Getter
    private String path;

    private boolean isPlay;

    private int chunkLength = Common.DEFAULT_CHUNK_MESSAGE_LENGTH;

    public void setControl(byte[] messageData, ChannelHandlerContext ctx,int chunkLength) {
        this.chunkLength = chunkLength;
        setAmfClass(messageData);
        this.ctx = ctx;
        msg = AMFUtil.load_amf_string(amfClass);
        txid = AMFUtil.load_amf_number(amfClass);
        System.out.println(msg);
        if(msg.equals("connect")) {
            handlerConnect();
        } else if (msg.equals("createStream")) {
            byte[] status = {AMFUtil.writeNull()};
            byte[] version = AMFUtil.writeNumber(Common.STREAM_ID);
            handResult(txid, MsgType.MSG_CONTROL, status, version, 0, ctx);
        } else if (msg.equals("FCPublish")) {
            handFCpublish(amfClass, txid, ctx);
        }

        if (msg.equals("publish")) {
            handPublish(amfClass, txid, ctx);
        } else if (msg.equals("play")) {
            System.err.println("播放罗");
            handPlay(amfClass, txid, ctx);
        } else if (msg.equals("pause")) {
            System.err.println("这里执行了几次");
            handPause(amfClass, txid, ctx);
        }
    }


    private void handlerConnect() {
        Map<String, Object> data = AMFUtil.load_amf_object(amfClass);
        if (data.containsKey("app")) {
            System.out.println(data.toString());
            String app = data.get("app").toString();
            if (app.equals(Common.APP_NAME)) {
                ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
//                 byte[] resultString = AMFUtil.writeString("Stream Begin");
//                 byte[] resultString = AMFUtil.writeNumber(0); // vlc  错误的
                byte[] resultString = Common.writeUnsignedInt16(0);
                byteBuf.writeBytes(resultString);
                byteBuf.writeBytes(Common.intToByte(0));
                int length = byteBuf.readableBytes();
                byte[] beginData = new byte[length];
                byteBuf.readBytes(beginData);
                RtmpResponse.sendData(beginData, MsgType.MSG_USER_CONTROL, 0, ctx, 0,chunkLength);
                handConnect(txid, ctx);
            }
        }
    }


    /**
     * 用户链接
     *
     * @param txid
     * @param ctx
     */
    private void handConnect(double txid, ChannelHandlerContext ctx) {
        Map<String, Object> version = new HashMap<String, Object>();
        double capabilities = 255.0;
        double mode = 1.0;
        version.put("fmsVer", "FMS/4,5,1,484");
        version.put("capabilities", capabilities);
        version.put("mode", mode);
        byte[] versionByte = AMFUtil.writeObject(version);
        Map<String, Object> status = new HashMap<String, Object>();
        double objectEncoding = 3.0;
        status.put("level", "status");
        status.put("code", "NetConnection.Connect.Success");
        status.put("description", "Connection succeeded.");
        status.put("objectEncoding", objectEncoding);
        byte[] statusVersion = AMFUtil.writeObject(status);
        handResult(txid, MsgType.MSG_CONTROL, versionByte, statusVersion, 0, ctx);
    }


    /**
     * @param amfClass
     * @param txid
     * @param ctx
     */
    private void handFCpublish(AMFClass amfClass, double txid, ChannelHandlerContext ctx) {
        AMFUtil.load_amf(amfClass);
        String path = AMFUtil.load_amf_string(amfClass); //这个为发布的 url 协议
        this.path = path;
        Publish client = new Publish();
        client.path = path;
        client.publish = ctx;
        client.chunk_size = chunkLength; //客户端上来的 chunkLength 大小
        PublishGroup.setChannel(path, client);
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
        byteBuf.writeBytes(AMFUtil.writeString("onFCPublish"));
        byteBuf.writeBytes(AMFUtil.writeNumber(0.0));
        byteBuf.writeByte(AMFUtil.writeNull());
        int length = byteBuf.readableBytes();
        byte[] version = new byte[length];
        byteBuf.readBytes(version);

        Map<String, Object> status = new HashMap<String, Object>();
        status.put("code", "NetStream.Publish.Start");
        status.put("description", path);
        byte[] statusData = AMFUtil.writeObject(status);
        handData(MsgType.MSG_CONTROL, version, statusData, 0, ctx);

        handResult(txid, MsgType.MSG_CONTROL, new byte[]{AMFUtil.writeNull()}, new byte[]{AMFUtil.writeNull()}, 0, ctx);
        byteBuf.release();
    }


    /**
     * @param amfClass
     * @param txid
     * @param ctx
     */
    private void handPublish(AMFClass amfClass, double txid, ChannelHandlerContext ctx) {
        AMFUtil.load_amf(amfClass);
        String path = AMFUtil.load_amf_string(amfClass); //这个为发布的 url 协议
        System.out.println(path);
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
        byteBuf.writeBytes(AMFUtil.writeString("onStatus"));
        byteBuf.writeBytes(AMFUtil.writeNumber(0.0));
        byteBuf.writeByte(AMFUtil.writeNull());
        int length = byteBuf.readableBytes();
        byte[] version = new byte[length];
        byteBuf.readBytes(version);
        Map<String, Object> status = new HashMap<String, Object>();
        status.put("level", "status");
        status.put("code", "NetStream.Publish.Start");
        status.put("description", "Stream is now published.");
        status.put("details", path);
        byte[] statusData = AMFUtil.writeObject(status);
        handData(MsgType.MSG_CONTROL, version, statusData, 0, ctx);

        handResult(txid, MsgType.MSG_CONTROL, new byte[]{AMFUtil.writeNull()}, new byte[]{AMFUtil.writeNull()}, 0, ctx);
        byteBuf.release();
    }

    /**
     * @param amfClass
     * @param txid
     * @param ctx
     */
    private void handPlay(AMFClass amfClass, double txid, ChannelHandlerContext ctx) {
        AMFUtil.load_amf(amfClass);
        String path = AMFUtil.load_amf_string(amfClass); //这个为发布的 url 协议
        this.path = path;
        isPlay = true;
        startPlayback(ctx, false);
        handResult(txid, MsgType.MSG_CONTROL, new byte[]{AMFUtil.writeNull()}, new byte[]{AMFUtil.writeNull()}, 0, ctx);
    }

    private void startPlayback(ChannelHandlerContext ctx, boolean isPause) {
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
        byteBuf.writeBytes(AMFUtil.writeString("onStatus"));
        byteBuf.writeBytes(AMFUtil.writeNumber(0.0));
        byteBuf.writeByte(AMFUtil.writeNull());
        int length = byteBuf.readableBytes();
        byte[] version = new byte[length];
        byteBuf.readBytes(version);

        Map<String, Object> status = new HashMap<String, Object>();
        status.put("level", "status");
        status.put("code", "NetStream.Play.Reset");
        status.put("description", "Resetting and playing stream.");

        byte[] statusData = AMFUtil.writeObject(status);
        handData(MsgType.MSG_CONTROL, version, statusData, 1337, ctx);
        if (!isPause) {
            status = new HashMap<String, Object>();
            status.put("level", "status");
            status.put("code", "NetStream.Play.Start");
            status.put("description", "Started playing.");
            statusData = AMFUtil.writeObject(status);

            byteBuf.writeBytes(AMFUtil.writeString("onStatus"));
            byteBuf.writeBytes(AMFUtil.writeNumber(0.0));
            byteBuf.writeByte(AMFUtil.writeNull());
            length = byteBuf.readableBytes();
            version = new byte[length];
            byteBuf.readBytes(version);
            handData(MsgType.MSG_CONTROL, version, statusData, 1337, ctx);

            Receive receive = new Receive();
            receive.receive = ctx;
            receive.playing = true;
            receive.ready = false;
            ReceiveGroup.setChannel(path, receive);

            Publish publish = PublishGroup.getChannel(path);
            if (publish != null) {
                System.out.println("keyframe");
                receive.keyframe = false;
                byteBuf.writeBytes(AMFUtil.writeString("onMetaData"));
                byteBuf.writeBytes(AMFUtil.writeMixedArray(publish.MetaData));
                byte[] resultData = new byte[byteBuf.readableBytes()];
                byteBuf.readBytes(resultData);
                RtmpResponse.sendData(resultData, MsgType.MSG_NOTIFY, 1337, ctx, 0,chunkLength);
                System.err.println("数据大小" + publish.chunk_size);
//            handSetChunkSize(ctx,chunkLength);
//            this.sendChunkLength = publish.chunk_size;
            }

        }
        byteBuf.release();
    }


    /**
     * 播放还是停止
     *
     * @param amfClass
     * @param txid
     * @param ctx
     */
    private void handPause(AMFClass amfClass, double txid, ChannelHandlerContext ctx) {
        AMFUtil.load_amf(amfClass); /* NULL */
        System.err.println("暂时或者开启服务");
        Boolean paused = AMFUtil.load_amf_boolean(amfClass);
        List<Receive> list = ReceiveGroup.getChannel(this.path);
        int listIndex = 0;
        for (Receive receive : list) {
            if (receive.receive == ctx) {
                if (receive.playing) {
                    System.err.println("没有播放");
                    receive.playing = false;
                } else {
                    System.err.println("开始播放");
                    receive.playing = true;
                }
                list.set(listIndex, receive);
            }
            listIndex++;
        }
        if (paused) {
            System.err.println("pausing\n");
            ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
            byteBuf.writeBytes(AMFUtil.writeString("onStatus"));
            byteBuf.writeBytes(AMFUtil.writeNumber(0.0));
            byteBuf.writeByte(AMFUtil.writeNull());
            byte[] version = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(version);
            Map<String, Object> status = new HashMap<String, Object>();
            status.put("level", "status");
            status.put("code", "NetStream.Pause.Notify");
            status.put("description", "Pausing.");
            byte[] statusData = AMFUtil.writeObject(status);
            handResult(txid, MsgType.MSG_CONTROL, version, statusData, 0, ctx);
            byteBuf.release();
//            client->playing = false;
        } else {
            System.out.println("进来了");
            startPlayback(ctx, true);
        }
        handResult(txid, MsgType.MSG_CONTROL, new byte[]{AMFUtil.writeNull()}, new byte[]{AMFUtil.writeNull()}, 0, ctx);
    }



    /**
     * 统一 _result 返回
     *
     * @param txid
     * @param msgType
     * @param version
     * @param status
     * @param streamId
     * @param ctx
     */
    private void handResult(double txid, byte msgType, byte[] version, byte[] status, int streamId, ChannelHandlerContext ctx) {
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
        byte[] resultString = AMFUtil.writeString("_result");
        byteBuf.writeBytes(resultString);
        byte[] resultNumber = AMFUtil.writeNumber(txid);
        byteBuf.writeBytes(resultNumber);
        byteBuf.writeBytes(version);
        byteBuf.writeBytes(status);
        int length = byteBuf.readableBytes();
        byte[] data = new byte[length];
        byteBuf.readBytes(data);
        RtmpResponse.sendData(data, msgType, streamId, ctx, 0,chunkLength);
        byteBuf.release();
    }


    /**
     * 统一数据返回格式
     *
     * @param msgType
     * @param version
     * @param status
     * @param streamId
     * @param ctx
     */
    private void handData(byte msgType, byte[] version, byte[] status, int streamId, ChannelHandlerContext ctx) {
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
        byteBuf.writeBytes(version);
        byteBuf.writeBytes(status);
        int length = byteBuf.readableBytes();
        byte[] data = new byte[length];
        byteBuf.readBytes(data);
        RtmpResponse.sendData(data, msgType, streamId, ctx, 0,chunkLength);
        byteBuf.release();
    }
}
