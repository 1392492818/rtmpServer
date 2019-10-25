package Decoder;

import AMF.*;
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

import java.util.*;

public class RtmpDecoder extends ByteToMessageDecoder {

    //chunk head 数据
    private List<Byte> chunkData = new ArrayList<Byte>(); // chunk所有数据， 包含 header
    private int chunkHeadIndex = 0; //byte head 提取的下标
    private int timestamp = 0; // 时间戳
    private int msgLength = 0; //整个chunk数据长度，不包含 header
    private int allMsglength = 0;
    private byte msgType; //消息类型
    private int streamId = 0;
    private boolean isExtendedTimestamp = false;

    //  chunk message 数据
    private List<Byte> chunkMessage = new ArrayList<Byte>(); //chunk 实际数据
    private int chunkMessageIndex = 0; //byte 具体数据 获取下标
    private int MT ;
    private int payloadLength = 0;
    private int strameId = 0;
    private ByteBuf byteBuf = null;
    //当数据不足msg length 时候的处理
    private byte[] allMessageData = null;
    private int readMessageIndex = 0;

    //网络传输数据包不足时候的处理
    private boolean lackMessage = false;

    private boolean isVersion = false; //是否认证了版本信息
    private boolean isC1 = false; //是否认证了 c1
    private boolean isC2 = false; //是否认证了 c2

    private byte[] c1Data = null;

    private int head_len = 0;
    private boolean lackHeadMessage = false;
    private int csid = 0;
    private Map<Integer,Integer> csIdMap = new HashMap<Integer, Integer>();
    private Map<Integer,Integer> timeMap = new HashMap<Integer, Integer>();


    //握手数据
    private List<Byte> handshakeData = new ArrayList<Byte>();
    private boolean isHandshake = false;
    private boolean isSendS1 = false;
    private List<Byte> S1 = new ArrayList<Byte>();
    private List<Byte> S2 = new ArrayList<Byte>();
    private byte[] zero = {0x00,0x00,0x00,0x00};

    private String path = null;

    private Map<String,Object> MetaData = null;
    @Override


    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {

//        System.err.println("可读字节 " + in.readableBytes());
        byteBuf = in;
        if(!isHandshake) { //rtmp 握手认证
            handshake(ctx);
        } else {
            handChunkMessage(ctx);
        }
    }

    /**
     * 解析chunkMessage 数据
     * @param ctx
     */
    private void handChunkMessage(ChannelHandlerContext ctx) {
        List<Byte> headData = new ArrayList<Byte>();
        try{
            if(!lackMessage) {
                if(!lackHeadMessage) {
                    //  System.out.println(Common.bytes2hex(Common.conversionByteArray(chunkData)));
                    byte[] flags = new byte[1];
                    byteBuf.readBytes(flags);
                    headData.add(flags[0]);
                    int[]  chunk_head_length = {12,8,4,1}; //对应的 chunk head length 长度
                    // byte fmtByte =  (byte)((byte)flags >> 6); //向右移动 6 位 获取 fmt
                    int fmt =  (byte) ((flags[0] & 0xff & 0xff) >> 6);
                    int csidTS = (byte)((flags[0] & 0xff & 0xff) & 0x3f); // 按位与 11 为 1 ，有0 为 0
                    this.csid = csidTS;
                    this.head_len = chunk_head_length[fmt];
                    int basic_head_len = chunkHeadIndex = getBasicHeadLength(csidTS);
                    byte[] chunkDataByte = Common.conversionByteArray(chunkData);
                }
                if(byteBuf.readableBytes() < this.head_len) { // 当byte长度不足时候，跳出函数等待数据
                    lackHeadMessage = true;
                    return;
                }
                lackHeadMessage = false;
                if((this.head_len == 1 || this.head_len == 4) && this.allMessageData == null){
                    this.msgLength =  this.csIdMap.get(this.csid);
                    this.allMsglength = this.csIdMap.get(this.csid);
                    this.allMessageData = new byte[this.allMsglength];
                }
                //   System.out.println("head" + head_len + " msg Length " + this.msgLength);
                if(head_len >= 4) { // 大于 1 先提取出 timestamp
                    byte[] timestampByte = new byte[Common.TIMESTAMP_BYTE_LENGTH];
                    byteBuf.readBytes(timestampByte);
                    for(byte i : timestampByte){
                        headData.add(i);
                    }
                    int ts  =  Common.byteToInt24(timestampByte);
                    if(timeMap.containsKey(csid)) { //如果存在那么 拿出之前的时间戳
                        if(head_len < 12) {
                            ts += timeMap.get(csid);
                        }
                        this.timestamp = ts;
                        timeMap.put(csid,this.timestamp);
                    } else {
                        timestamp = ts;  //如果不存在 csid 在列表中， 那么时间戳直接赋值就可以
                        timeMap.put(csid,this.timestamp);
                    }

                    if(timestamp == Common.TIMESTAMP_MAX_NUM) {
                        isExtendedTimestamp = true; // 前3个字节放不下，放在最后面的四个字节
                    }
                    chunkHeadIndex = chunkHeadIndex + Common.TIMESTAMP_BYTE_LENGTH;
                }
                if(head_len >= 8) {
                    // 大于 4 先提取出 msgLength
                    byte[] msg_len = new byte[Common.TIMESTAMP_BYTE_LENGTH];
                    byteBuf.readBytes(msg_len);
                    this.msgLength = Common.byteToInt24(msg_len);
                    this.allMsglength = Common.byteToInt24(msg_len);
                    this.allMessageData = new byte[allMsglength];
                    this.csIdMap.put(this.csid,this.allMsglength);
                    for(byte i : msg_len){
                        headData.add(i);
                    }
                    // 提取msgType
                    byte[] msgTypeByte = new byte[1];
                    byteBuf.readBytes(msgTypeByte);
                    this.msgType = msgTypeByte[0];
                    for(byte i : msgTypeByte){
                        headData.add(i);
                    }
                }

                if(head_len >= 12) {
                    byte[] streamByte = new byte[Common.STREAM_ID_LENGTH];
                    byteBuf.readBytes(streamByte);
                    System.out.println(Common.bytes2hex(streamByte));
                    this.streamId = Common.byteSmallToInt(streamByte); //只有 stream 是小端模式
                    for(byte i : streamByte){
                        headData.add(i);
                    }
                    //System.out.println("streamId === " + streamId);
                }

                System.out.println("消息类型 === " + Integer.toHexString(this.msgType) + " msg len " + this.msgLength + " time " + timestamp +" head_len " + head_len + " streamId === " + streamId);
//                System.out.println("head mess " + Common.bytes2hex(headData) );
                if(isExtendedTimestamp) {
                    byte[] timestampByte = new byte[Common.EXTEND_TIMESTAMP_LENGTH];
                    byteBuf.readBytes(timestampByte);
                    this.timestamp = Common.byteToInt24(timestampByte);
                    for(byte i : timestampByte){
                        headData.add(i);
                    }
                }
            }

            int msgIndex = msgLength > Common.DEFAULT_CHUNK_MESSAGE_LENGTH ?  Common.DEFAULT_CHUNK_MESSAGE_LENGTH :  msgLength;
            if(byteBuf.readableBytes() < msgIndex){  //如果不够，需要等到数据足够再读取
                //    System.err.println("数据不足了");
                lackMessage = true;
                return;
            }
            lackMessage = false;
            byte[] messageData = new byte[msgIndex]; //这里是其中一部分数据，分包需要重复提取到完整为止
            byteBuf.readBytes(messageData);
            int index = 0;
            for(int i = this.readMessageIndex; i < this.readMessageIndex + msgIndex;i++){
                this.allMessageData[i] = messageData[index];
                index++;
            }
            this.readMessageIndex += msgIndex;
            if(this.readMessageIndex < allMsglength){ //还没有提取完所有数据
                msgLength = allMsglength - this.readMessageIndex;
            } else {
                System.out.println("解析的数据" + this.allMessageData.length);
                handMessage(this.allMessageData,ctx);
//                if(this.msgLength > 0) {
//                    this.allMessageData = new byte[this.msgLength];
//                    this.allMsglength = this.msgLength;
//                }
                this.allMessageData = null;
                this.msgLength = 0;
                this.readMessageIndex = 0;
                isExtendedTimestamp = false;
            }
            if(byteBuf.readableBytes() > 0) {
                handChunkMessage(ctx);
            }
        }catch (Exception e) {
            ctx.close();
            e.printStackTrace();
            System.err.println(Common.bytes2hex(Common.conversionByteArray(chunkData)));
        }

    }

    /**
     * 用户链接
     * @param txid
     * @param ctx
     */
    private void handConnect(double txid,ChannelHandlerContext ctx) {
        Map<String,Object> version = new HashMap<String, Object>();
        double capabilities = 255.0;
        double mode = 1.0;
        version.put("fmsVer","FMS/4,5,1,484");
        version.put("capabilities",capabilities);
        version.put("mode",mode);
        byte[] versionByte = AMFUtil.writeObject(version);
        Map<String,Object> status = new HashMap<String, Object>();
        double objectEncoding = 3.0;
        status.put("level","status");
        status.put("code","NetConnection.Connect.Success");
        status.put("description","Connection succeeded.");
        status.put("objectEncoding",objectEncoding);
        byte[] statusVersion = AMFUtil.writeObject(status);
        handResult(txid, MsgType.msg14,versionByte,statusVersion,0,ctx);
    }

    /**
     *
     * @param amfClass
     * @param txid
     * @param ctx
     */
    private void handPublish(AMFClass amfClass,double txid,ChannelHandlerContext ctx) {
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
        Map<String,Object> status = new HashMap<String, Object>();
        status.put("level","status");
        status.put("code","NetStream.Publish.Start");
        status.put("description","Stream is now published.");
        status.put("details",path);
        byte[] statusData = AMFUtil.writeObject(status);
        handData(MsgType.msg14,version,statusData,0,ctx);

        handResult(txid,MsgType.msg14,new byte[]{AMFUtil.writeNull()},new byte[]{AMFUtil.writeNull()},0,ctx);
    }

    /**
     *
     * @param amfClass
     * @param txid
     * @param ctx
     */
    private void handPlay(AMFClass amfClass,double txid,ChannelHandlerContext ctx) {
        AMFUtil.load_amf(amfClass);
        String path = AMFUtil.load_amf_string(amfClass); //这个为发布的 url 协议
        this.path = path;
        System.out.println(path);
        Receive receive = new Receive();
        receive.receive = ctx;
        receive.playing = true;
        ReceiveGroup.setChannel(path,receive);
        startPlayback(ctx);
        handResult(txid,MsgType.msg14,new byte[]{AMFUtil.writeNull()},new byte[]{AMFUtil.writeNull()},0,ctx);
    }

    private void startPlayback(ChannelHandlerContext ctx) {
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
        byteBuf.writeBytes(AMFUtil.writeString("onStatus"));
        byteBuf.writeBytes(AMFUtil.writeNumber(0.0));
        byteBuf.writeByte(AMFUtil.writeNull());
        int length = byteBuf.readableBytes();
        byte[] version = new byte[length];
        byteBuf.readBytes(version);

        Map<String,Object> status = new HashMap<String, Object>();
        status.put("level","status");
        status.put("code","NetStream.Play.Reset");
        status.put("description","Resetting and playing stream.");

        byte[] statusData = AMFUtil.writeObject(status);
        handData(MsgType.msg14,version,statusData,0,ctx);

        status = new HashMap<String, Object>();
        status.put("level","status");
        status.put("code","NetStream.Play.Start");
        status.put("description","Started playing.");
        statusData = AMFUtil.writeObject(status);

        byteBuf.writeBytes(AMFUtil.writeString("onStatus"));
        byteBuf.writeBytes(AMFUtil.writeNumber(0.0));
        byteBuf.writeByte(AMFUtil.writeNull());
        length = byteBuf.readableBytes();
        version = new byte[length];
        byteBuf.readBytes(version);
        handData(MsgType.msg14,version,statusData,0,ctx);
//
//                    client->playing = true;
//                    client->ready = false;
//
//                    if (publisher != NULL) {
//                        Encoder notify;
//                        amf_write(&notify, std::string("onMetaData"));
//                        amf_write_ecma(&notify, metadata);
//                        rtmp_send(client, MSG_NOTIFY, STREAM_ID, notify.buf);
//                    }
    }

    /**
     *
     * @param amfClass
     * @param txid
     * @param ctx
     */
    private void handFCpublish(AMFClass amfClass,double txid,ChannelHandlerContext ctx) {
        AMFUtil.load_amf(amfClass);
        String path = AMFUtil.load_amf_string(amfClass); //这个为发布的 url 协议
        this.path = path;
        Publish client = PublishGroup.getChannel(path);
        if(client == null) {
            client = new Publish();
            client.path = path;
            client.publish = ctx;
        }
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
        byteBuf.writeBytes(AMFUtil.writeString("onFCPublish"));
        byteBuf.writeBytes(AMFUtil.writeNumber(0.0));
        byteBuf.writeByte(AMFUtil.writeNull());
        int length = byteBuf.readableBytes();
        byte[] version = new byte[length];
        byteBuf.readBytes(version);

        Map<String,Object> status = new HashMap<String, Object>();
        status.put("code","NetStream.Publish.Start");
        status.put("description",path);
        byte[] statusData = AMFUtil.writeObject(status);
        handData(MsgType.msg14,version,statusData,0,ctx);

        handResult(txid,MsgType.msg14,new byte[]{AMFUtil.writeNull()},new byte[]{AMFUtil.writeNull()},0,ctx);
    }

    /**
     * 播放还是停止
     * @param amfClass
     * @param txid
     * @param ctx
     */
    private void handPause(AMFClass amfClass,double txid,ChannelHandlerContext ctx) {
        AMFUtil.load_amf(amfClass); /* NULL */

        Boolean paused = AMFUtil.load_amf_boolean(amfClass);

        if (paused) {
            System.out.println("pausing\n");

            ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
            byteBuf.writeBytes(AMFUtil.writeString("onStatus"));
            byteBuf.writeBytes(AMFUtil.writeNumber(0.0));
            byteBuf.writeByte(AMFUtil.writeNull());
            byte[] version = new byte[byteBuf.readableBytes()];
            byteBuf.readBytes(version);
            Map<String,Object> status = new HashMap<String, Object>();
            status.put("level","status");
            status.put("code","NetStream.Pause.Notify");
            status.put("description","Pausing.");
            byte[] statusData = AMFUtil.writeObject(status);
            handResult(txid,MsgType.msg14,version,statusData,0,ctx);

//            client->playing = false;
        } else {
            startPlayback(ctx);
        }
        handResult(txid,MsgType.msg14,new byte[]{AMFUtil.writeNull()},new byte[]{AMFUtil.writeNull()},0,ctx);
    }

    /**
     * 处理message 消息
     * @param message
     * @param ctx
     */
    private void handMessage(byte[] message,ChannelHandlerContext ctx) {
        AMFClass amfClass = new AMFClass();
        amfClass.message = message;
        amfClass.pos = 0;
        switch (msgType) {
            case 0x14:
                //  System.out.println("消息控制服务");
                String msg = AMFUtil.load_amf_string(amfClass);
                double txid = AMFUtil.load_amf_number(amfClass);
                System.out.println(msg);

                if(this.streamId == Common.CONTROL_ID) {
                    if(msg.equals("connect")) {
                        Map<String,Object> data = AMFUtil.load_amf_object(amfClass);
                        if(data.containsKey("app")) {
                            String app = data.get("app").toString();
                            if(app.equals(Common.APP_NAME)){
                                handConnect(txid,ctx);
                            }
                        }
                    } else if(msg.equals("createStream")) {
                        byte[] status = {AMFUtil.writeNull()};
                        byte[] version = AMFUtil.writeNumber(Common.STREAM_ID);
                        handResult(txid, MsgType.msg14,status,version,0,ctx);
                    } else if(msg.equals("FCPublish")) {
                        handFCpublish(amfClass,txid,ctx);
                    }
                }

                if(this.streamId == Common.STREAM_ID) {
                    if(msg.equals("publish")) {
                        handPublish(amfClass,txid,ctx);
                    } else if(msg.equals("play")) {
                        handPlay(amfClass,txid,ctx);
                    } else if(msg.equals("pause")){
                        handPause(amfClass,txid,ctx);
                    }
                }
                break;
            case 0x12: //设置一些元信息
                String command  = AMFUtil.load_amf_string(amfClass);
                System.out.println(command);
                if(command.equals("@setDataFrame")){
                    String type = AMFUtil.load_amf_string(amfClass);
                    System.out.println(type);
                    Map<String,Object> data = AMFUtil.load_amf_mixedArray(amfClass);
                    this.MetaData = data;
                }
            case 0x08:
                List<Receive> list = ReceiveGroup.getChannel(this.path);
                for(Receive receive: list){
                    sendData(message,MsgType.MSGAUDIO,this.streamId,receive.receive);
                }
//                FOR_EACH(std::vector<Client *>, i, clients) {
//                Client *receiver = *i;
//                if (receiver != NULL && receiver->ready) {
//                    rtmp_send(receiver, MSG_AUDIO, STREAM_ID,
//                            msg->buf, msg->timestamp);
//                }
//                 }
                break;
            case 0x09:
                break;
            default:
                break;
        }
    }

    /**
     * 统一数据返回格式
     * @param msgType
     * @param version
     * @param status
     * @param streamId
     * @param ctx
     */
    private void handData(byte msgType,byte[] version,byte[] status,int streamId,ChannelHandlerContext ctx) {
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(1024);
        byteBuf.writeBytes(version);
        byteBuf.writeBytes(status);
        int length = byteBuf.readableBytes();
        byte[] data = new byte[length];
        byteBuf.readBytes(data);
        sendData(data,msgType,streamId,ctx);
    }

    /**
     * 统一 _result 返回
     * @param txid
     * @param msgType
     * @param version
     * @param status
     * @param streamId
     * @param ctx
     */
    private void handResult(double txid,byte msgType,byte[] version,byte[] status,int streamId,ChannelHandlerContext ctx) {
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
        sendData(data,msgType,streamId,ctx);
    }


    /**
     * 同意数据发送
     * @param chunkData
     * @param msgType
     * @param streamId
     * @param ctx
     */
    private void sendData(byte[] chunkData,byte msgType,int streamId,ChannelHandlerContext ctx) {
        ByteBuf byteBuf = ByteBufAllocator.DEFAULT.buffer(128);
        byte flags = (3 & 0x3f) | (0 << 6);
        byte[] timestamp = {0x00,0x00,0x00};
        int msg_len = chunkData.length;
        byte[] msgLength = Common.reverseArray(Common.intToByte24(msg_len));
        byte[] streamData = Common.intToByte(streamId);

        byteBuf.writeByte(flags);
        byteBuf.writeBytes(timestamp);
        byteBuf.writeBytes(msgLength);
        byteBuf.writeByte(msgType);
        byteBuf.writeBytes(streamData);
        int pos = 0;
        while(pos < chunkData.length){
            if(byteBuf.writableBytes() < chunkData.length) {
                byteBuf.ensureWritable(chunkData.length);
            }
            if(chunkData.length - pos < Common.DEFAULT_CHUNK_MESSAGE_LENGTH){
                for(int i = pos; i < chunkData.length;i++){
                    byteBuf.writeByte(chunkData[i]);
                }
            } else {
                if(byteBuf.writableBytes() < pos + 128) {
                    byteBuf.ensureWritable(pos + 128);
                }
                for(int i = pos; i < pos + 128;i++) {
                    byteBuf.writeByte(chunkData[i]);
                }
                byteBuf.writeByte((byte) ((3 & 0x3f) | (3 << 6)));
            }
            pos += Common.DEFAULT_CHUNK_MESSAGE_LENGTH;
        }
        int sendLength = byteBuf.readableBytes();
        byte[] sendData = new byte[sendLength];
        byteBuf.readBytes(sendData);
        ctx.writeAndFlush(Unpooled.copiedBuffer(sendData));
    }

    /**
     * 根据csidTS 获取 basic head 长度
     * @param csidTs
     * @return
     */
    private int getBasicHeadLength(int csidTs) {
        if(csidTs == 0) {
            return 2;
        }
        if(csidTs == 0x3f) {
            return 3;
        }
        return 1;
    }


    /**
     * 解析握手数据
     * @param ctx
     */
    private void handshake(ChannelHandlerContext ctx) {
        if(!isVersion) { // 认证 version
            byte[] flags = new byte[1];
            byteBuf.readBytes(flags);
            if(flags[0] != Common.C0){
                ctx.close();
                return;
            } else {
                isVersion = true;
            }
        }

        if(!isC1) {
            if(byteBuf.readableBytes() >= Common.C1_LENGTH){
                this.c1Data = new byte[Common.C1_LENGTH];
                byteBuf.readBytes(this.c1Data); //读取c1 内容
                int time = (int) (new Date().getTime() / 1000);
                byte[] timeByte = Common.intToByte(time);
                ctx.writeAndFlush(Unpooled.copiedBuffer(new byte[]{Common.S0}));
                for(byte i : timeByte){
                    S1.add(i);
                }
                for(byte i: zero){
                    S1.add(i);
                }
                for(int i = 0; i < Common.RANDOM_LENGTH;i++) {
                    Random random = new Random();
                    S1.add((byte) random.nextInt(9));
                }
                ctx.writeAndFlush(Unpooled.copiedBuffer(Common.conversionByteArray(S1)));
                isC1 = true;

                int s2time = (int) (new Date().getTime() / 1000); //设置 s2 time
                byte[] timeByte2 = Common.intToByte(s2time);
                int index  = 0;
                for(int i = 4; i < 8;i++) {
                    this.c1Data[i] = timeByte2[index];
                    index++;
                }
                ctx.writeAndFlush(Unpooled.copiedBuffer(this.c1Data));
            }
        }
        if(!isC2){
            if(byteBuf.readableBytes() >= Common.C1_LENGTH){
                byte[] s2 = new byte[Common.C1_LENGTH];
                byteBuf.readBytes(s2); //读取c1 内容
                isHandshake = true;
            }
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        System.out.println(cause.getMessage());
        super.exceptionCaught(ctx, cause);
    }

//    private void sendData(List<Byte> chunkData,ChannelHandlerContext ctx) {
//        List<Byte> rtmpHead = new ArrayList<Byte>();
//        byte flags = (3 & 0x3f) | (0 << 6);
//        rtmpHead.add(flags);
//        byte[] timestamp = {0x00,0x00,0x00};
//        for(byte i: timestamp){
//            rtmpHead.add(i);
//        }
//        int msg_len = chunkData.size();
//        byte[] msgLength = Common.intToByte(msg_len);
//        rtmpHead.add(msgLength[2]);
//        rtmpHead.add(msgLength[1]);
//        rtmpHead.add(msgLength[0]);
//        byte msg_type = 0x14;
//        rtmpHead.add(msg_type);
//        rtmpHead.add((byte) 0x00);
//        rtmpHead.add((byte) 0x00);
//        rtmpHead.add((byte) 0x00);
//        rtmpHead.add((byte) 0x00);
//        List<Byte> chunk = new ArrayList<Byte>();
//        for(int i = 0; i < rtmpHead.size(); i++){
//            chunk.add(rtmpHead.get(i));
//        }
//        int pos = 0;
//        while(pos < chunkData.size()){
//            if(chunkData.size() - pos < Common.DEFAULT_CHUNK_MESSAGE_LENGTH){
//                for(int i = pos; i < chunkData.size();i++){
//                    chunk.add(chunkData.get(i));
//                }
//            } else {
//                for(int i = pos; i < pos + 128;i++) {
//                    chunk.add(chunkData.get(i));
//                }
//                chunk.add((byte) ((3 & 0x3f) | (3 << 6)));
//            }
//            pos += Common.DEFAULT_CHUNK_MESSAGE_LENGTH;
//        }
//        ctx.writeAndFlush(Unpooled.copiedBuffer(Common.conversionByteArray(chunk)));
//    }
}