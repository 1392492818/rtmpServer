package Decoder;

import AMF.AMFClass;
import AMF.AMFUtil;
import Rtmp.RtmpHandshake;
import User.Publish;
import User.PublishGroup;
import User.Receive;
import User.ReceiveGroup;
import Util.Common;
import Util.MsgType;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static Util.Common.FLV_KEY_FRAME;

public class RtmpDecoderbak extends ByteToMessageDecoder {

    //chunk head 数据
    private List<Byte> chunkData = new ArrayList<Byte>(); // chunk所有数据， 包含 header
    private int chunkHeadIndex = 0; //byte head 提取的下标
    private int timestamp = 0; // 时间戳
    private int sendTimestamp = 0;
    private int msgLength = 0; //整个chunk数据长度，不包含 header
    private int allMsglength = 0;
    private byte msgType; //消息类型
    private int streamId = 0;
    private boolean isExtendedTimestamp = false;

    //  chunk message 数据
    private ByteBuf byteBuf = null;
    //当数据不足msg length 时候的处理
    private byte[] allMessageData = null;
    private int readMessageIndex = 0;

    //网络传输数据包不足时候的处理
    private boolean lackMessage = false;


    private int head_len = 0;
    private boolean lackHeadMessage = false;
    private int csid = 0;
    private Map<Integer, Integer> csIdMap = new HashMap<Integer, Integer>();
    private Map<Integer, Integer> timeMap = new HashMap<Integer, Integer>();
    private Map<Integer, Byte> msyTypeMap = new HashMap<Integer, Byte>();


    //握手数据
    private RtmpHandshake rtmpHandshake = new RtmpHandshake();

    private String path = null;
    private int chunkLength = Common.DEFAULT_CHUNK_MESSAGE_LENGTH;
    private int sendChunkLength = Common.DEFAULT_CHUNK_MESSAGE_LENGTH;

    private boolean isPlay = false;

    private ByteBuf streamByteBuf = ByteBufAllocator.DEFAULT.buffer(1024);

    AudioStreamDecoder audioStreamDecoder = new AudioStreamDecoder();


    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {

//        System.err.println("可读字节 " + in.readableBytes());
        byteBuf = in;
        if (!rtmpHandshake.isHandshake) { //rtmp 握手认证
            //handshake(ctx);
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
        List<Byte> headData = new ArrayList<Byte>();
        try {
            if (!lackMessage) {
                if (!lackHeadMessage) {
                    //  System.out.println(Common.bytes2hex(Common.conversionByteArray(chunkData)));
                    byte[] flags = new byte[1];
                    byteBuf.readBytes(flags);
                    headData.add(flags[0]);
                    int[] chunk_head_length = {12, 8, 4, 1}; //对应的 chunk head length 长度
                    // byte fmtByte =  (byte)((byte)flags >> 6); //向右移动 6 位 获取 fmt
                    int fmt = (byte) ((flags[0] & 0xff & 0xff) >> 6);
                    int csidTS = (byte) ((flags[0] & 0xff & 0xff) & 0x3f); // 按位与 11 为 1 ，有0 为 0
                    this.csid = csidTS;
                    this.head_len = chunk_head_length[fmt];
                    int basic_head_len = chunkHeadIndex = getBasicHeadLength(csidTS);
                    byte[] chunkDataByte = Common.conversionByteArray(chunkData);
                }
                if (byteBuf.readableBytes() < this.head_len) { // 当byte长度不足时候，跳出函数等待数据
                    lackHeadMessage = true;
                    return;
                }
                lackHeadMessage = false;
                if ((this.head_len == 1 || this.head_len == 4) && this.allMessageData == null) {
                    this.msgLength = this.csIdMap.get(this.csid);
                    this.allMsglength = this.csIdMap.get(this.csid);
                    this.allMessageData = new byte[this.allMsglength];
                }
                //   System.out.println("head" + head_len + " msg Length " + this.msgLength);
                if (head_len >= 4) { // 大于 1 先提取出 timestamp
                    byte[] timestampByte = new byte[Common.TIMESTAMP_BYTE_LENGTH];
                    byteBuf.readBytes(timestampByte);
                    for (byte i : timestampByte) {
                        headData.add(i);
                    }
                    int ts = Common.byteToInt24(timestampByte);
                    sendTimestamp = ts;
                    if (timeMap.containsKey(csid)) { //如果存在那么 拿出之前的时间戳
                        if (head_len < 12) {
                            ts += timeMap.get(csid);
                        }
                        this.timestamp = ts;
                        timeMap.put(csid, this.timestamp);
                    } else {
                        timestamp = ts;  //如果不存在 csid 在列表中， 那么时间戳直接赋值就可以
                        timeMap.put(csid, this.timestamp);
                    }

                    if (timestamp == Common.TIMESTAMP_MAX_NUM) {
                        isExtendedTimestamp = true; // 前3个字节放不下，放在最后面的四个字节
                    }
                    chunkHeadIndex = chunkHeadIndex + Common.TIMESTAMP_BYTE_LENGTH;
                }
                if (head_len >= 8) {
                    // 大于 4 先提取出 msgLength
                    byte[] msg_len = new byte[Common.TIMESTAMP_BYTE_LENGTH];
                    byteBuf.readBytes(msg_len);
                    this.msgLength = Common.byteToInt24(msg_len);
                    this.allMsglength = Common.byteToInt24(msg_len);
                    this.allMessageData = new byte[allMsglength];
                    this.csIdMap.put(this.csid, this.allMsglength);
                    for (byte i : msg_len) {
                        headData.add(i);
                    }
                    // 提取msgType
                    byte[] msgTypeByte = new byte[1];
                    byteBuf.readBytes(msgTypeByte);
                    this.msgType = msgTypeByte[0];
                    msyTypeMap.put(this.csid, this.msgType);
                    for (byte i : msgTypeByte) {
                        headData.add(i);
                    }
                }

                if (head_len >= 12) {
                    byte[] streamByte = new byte[Common.STREAM_ID_LENGTH];
                    byteBuf.readBytes(streamByte);
                    System.out.println(Common.bytes2hex(streamByte));
                    this.streamId = Common.byteSmallToInt(streamByte); //只有 stream 是小端模式
                    for (byte i : streamByte) {
                        headData.add(i);
                    }
                    //System.out.println("streamId === " + streamId);
                }
                if (isExtendedTimestamp) {
                    byte[] timestampByte = new byte[Common.EXTEND_TIMESTAMP_LENGTH];
                    byteBuf.readBytes(timestampByte);
                    this.timestamp = Common.byteToInt24(timestampByte);
                    for (byte i : timestampByte) {
                        headData.add(i);
                    }
                }
            }

            int msgIndex = msgLength > chunkLength ? chunkLength : msgLength;
            if (byteBuf.readableBytes() < msgIndex) {  //如果不够，需要等到数据足够再读取
                //    System.err.println("数据不足了");
                lackMessage = true;
                return;
            }
            lackMessage = false;
            byte[] messageData = new byte[msgIndex]; //这里是其中一部分数据，分包需要重复提取到完整为止
            byteBuf.readBytes(messageData);
            int index = 0;
            for (int i = this.readMessageIndex; i < this.readMessageIndex + msgIndex; i++) {
                this.allMessageData[i] = messageData[index];
                index++;
            }
            this.readMessageIndex += msgIndex;
            if (this.readMessageIndex < allMsglength) { //还没有提取完所有数据
                msgLength = allMsglength - this.readMessageIndex;
            } else {
                handMessage(this.allMessageData, ctx);
                this.allMessageData = null;
                this.msgLength = 0;
                this.readMessageIndex = 0;
                isExtendedTimestamp = false;
            }
            if (byteBuf.readableBytes() > 0) {
                handChunkMessage(ctx);
            }
        } catch (Exception e) {
            ctx.close();
            e.printStackTrace();
            System.err.println(Common.bytes2hex(Common.conversionByteArray(chunkData)));
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

            System.out.println(path);
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
                sendData(resultData, MsgType.MSG_NOTIFY, 1337, ctx, 0);
                System.err.println("数据大小" + publish.chunk_size);
//            handSetChunkSize(ctx,chunkLength);
//            this.sendChunkLength = publish.chunk_size;
            }

        }
        byteBuf.release();
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
        client.chunk_size = chunkLength;
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
     * 回复客户端设置 chunk 大小，OBS 如果没有回复此消息，不会继续推流
     *
     * @param ctx
     * @param chunkLength
     */
    private void handSetChunkSize(ChannelHandlerContext ctx, double chunkLength) {
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
        byte[] data = AMFUtil.writeNumber(chunkLength);
        byteBuf.writeBytes(data);
        int length2 = byteBuf.readableBytes();
        byte[] beginData2 = new byte[length2];
        byteBuf.readBytes(beginData2);
        sendData(beginData2, MsgType.MSG_CHUNK_SIZE, 0, ctx, 0);
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
     * 处理message 消息
     *
     * @param message
     * @param ctx
     */
    private void handMessage(byte[] message, ChannelHandlerContext ctx) {
        AMFClass amfClass = new AMFClass();
        amfClass.message = message;
        amfClass.pos = 0;
        int index = 0;
        byte[] number;
        byte msgType = msyTypeMap.get(this.csid);
        switch (msgType) {
            case 0x01:
                System.err.println("msg_set_chunk");
                if (amfClass.pos + 4 > amfClass.message.length) {
                    System.err.println("数据不足");
                }
                System.err.println(Common.bytes2hex(amfClass.message));
                number = new byte[4];
                index = 0;
                for (int i = amfClass.pos; i < amfClass.pos + 4; i++) {
                    number[index] = amfClass.message[i];
                    index++;
                }
                chunkLength = Common.byteToInt(number);
                handSetChunkSize(ctx, Common.DEFAULT_CHUNK_MESSAGE_LENGTH);
            case 0x03:
                System.err.println("read byte");
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
            case 0x05:
//                System.err.println("server window");
                if (amfClass.pos + 4 > amfClass.message.length) {
                    System.err.println("数据不足");
                }
//                System.err.println(Common.bytes2hex(amfClass.message));
                number = new byte[4];
                index = 0;
                for (int i = amfClass.pos; i < amfClass.pos + 4; i++) {
                    number[index] = amfClass.message[i];
                    index++;
                }
                int windowSize = Common.byteToInt(number);
                System.err.println("window size" + windowSize);
                break;
            case 0x14:
                System.err.println("消息控制服务");
                String msg = AMFUtil.load_amf_string(amfClass);
                double txid = AMFUtil.load_amf_number(amfClass);
                System.err.println("=======");
                System.err.println(msg);
                System.err.println(txid);
                System.err.println("========");

                if (this.streamId == Common.CONTROL_ID) {
                    if (msg.equals("connect")) {
                        Map<String, Object> data = AMFUtil.load_amf_object(amfClass);
                        if (data.containsKey("app")) {
                            System.out.println(data.toString());
                            String app = data.get("app").toString();
                            if (app.equals(Common.APP_NAME)) {
                                System.out.println(this.streamId);
                                //amfClass.pos = 0x02;
                                ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
                                byte[] resultString = AMFUtil.writeString("Stream Begin");
//                                byte[] resultString = AMFUtil.writeNumber(0); // vlc
//                                byte[] resultString = Common.writeUnsignedInt16(0);
                                byteBuf.writeBytes(resultString);
                                byteBuf.writeBytes(Common.intToByte(0));
                                int length = byteBuf.readableBytes();
                                byte[] beginData = new byte[length];
                                byteBuf.readBytes(beginData);
                                sendData(beginData, MsgType.MSG_USER_CONTROL, 0, ctx, 0);
                                handConnect(txid, ctx);
                            }
                        }
                    } else if (msg.equals("createStream")) {
                        byte[] status = {AMFUtil.writeNull()};
                        byte[] version = AMFUtil.writeNumber(Common.STREAM_ID);
                        handResult(txid, MsgType.MSG_CONTROL, status, version, 0, ctx);
                    } else if (msg.equals("FCPublish")) {
                        handFCpublish(amfClass, txid, ctx);
                    }
                }
                List<Receive> list2;
                int listIndex = 0;
                if (this.streamId == Common.STREAM_ID) {
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
                break;
            case 0x12: //设置一些元信息
                String command = AMFUtil.load_amf_string(amfClass);
                System.err.println(command);
                if (command.equals("@setDataFrame")) {
                    String type = AMFUtil.load_amf_string(amfClass);
                    System.out.println(type);
                    Map<String, Object> data = AMFUtil.load_amf_mixedArray(amfClass);
                    Publish publish = PublishGroup.getChannel(this.path);
                    if (publish != null) {
                        publish.MetaData = data;
                    }
//                    this.MetaData = data;
                }
            case 0x08:
//                System.err.println("音频" + message.length);
                audioStreamDecoder.decode(message);
                List<Receive> list = ReceiveGroup.getChannel(this.path);
                if (list != null) {
                    for (Receive receive : list) {
                        if (receive.ready && receive.keyframe) {
                            if (!receive.playing) {
                                continue;
                            }
                            int timestamp = timeMap.get(this.csid);
                             System.out.println("audio发送数据 ===" + message.length  +" 时间戳" + timestamp);
                            sendData2(message, MsgType.MSG_AUDIO, 1337, receive.receive, timestamp);
                        }
                    }
                }
                break;
            case 0x09:
//                System.err.println("视频" + message.length);
//                Common.appendMethodA("D:\\test.h264",message);
                new VideoStreamDecoder(message);
                byte flags = message[0];
//                System.out.println("=======================");
//                System.out.println(Common.bytes2hex(message));
//                System.out.println("=======================");
                Publish publish = PublishGroup.getChannel(path);
                if (!publish.keyFrame) {
                    publish.keyFrame = true;
                    publish.keyFrameMessage = message;
                }
                List<Receive> listVideo = ReceiveGroup.getChannel(this.path);
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
                                sendData(control, MsgType.MSG_USER_CONTROL, 0, receive.receive, 0);
                                receive.ready = true;
                                byteBuf.release();
                            }
                            if (!receive.keyframe) {
                                System.out.println("关键进来了");
                                receive.keyframe = true;
                                sendData2(publish.keyFrameMessage, MsgType.MSG_VIDEO, 1337, receive.receive, timestamp);
                            }
                            if (receive.ready && receive.keyframe) {
                                int timestamp = timeMap.get(this.csid);
                                //System.out.println("时间戳" + timestamp);
                                 System.out.println("video发送数据 ===" + message.length +" 时间戳" + timestamp);
                                sendData2(message, MsgType.MSG_VIDEO, 1337, receive.receive, timestamp);
                            }
                        }
                    }
                }
                break;
            default:
                System.err.println(this.msgType);
               // System.err.println(Common.bytes2hex(message));
                  System.out.println("有其他东西过来了");
                break;
        }
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
        sendData(data, msgType, streamId, ctx, 0);
        byteBuf.release();
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
        sendData(data, msgType, streamId, ctx, 0);
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
    private void sendData(byte[] chunkData, byte msgType, int streamId, ChannelHandlerContext ctx, int timestamp) {
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
    private void sendData2(byte[] chunkData, byte msgType, int streamId, ChannelHandlerContext ctx, int timestamp) {
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

    private void sendClient(ChannelHandlerContext ctx) {
        if (streamByteBuf.readableBytes() > 0) {
            int length = streamByteBuf.readableBytes();
            //if(length > 4096) length = 4096;
            byte[] sendData = new byte[length];
            streamByteBuf.readBytes(sendData);
            ctx.fireChannelRead(sendData);
        }
    }


    /**
     * 根据csidTS 获取 basic head 长度
     *
     * @param csidTs
     * @return
     */
    private int getBasicHeadLength(int csidTs) {
        if (csidTs == 0) {
            return 2;
        }
        if (csidTs == 0x3f) {
            return 3;
        }
        return 1;
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