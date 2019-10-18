package Decoder;

import AMF.*;
import Util.Common;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.omg.PortableInterceptor.SYSTEM_EXCEPTION;

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


    //握手数据
    private List<Byte> handshakeData = new ArrayList<Byte>();
    private boolean isHandshake = false;
    private boolean isSendS1 = false;
    private List<Byte> S1 = new ArrayList<Byte>();
    private List<Byte> S2 = new ArrayList<Byte>();
    private byte[] zero = {0x00,0x00,0x00,0x00};
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
                    timestamp =  Common.byteToInt24(timestampByte);
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
//                    System.out.println("消息类型 === " + Integer.toHexString(this.msgType) + " msg len " + this.msgLength + " time " + timestamp);
                    for(byte i : msgTypeByte){
                        headData.add(i);
                    }
                }

                if(head_len >= 12) {
                    byte[] streamByte = new byte[Common.STREAM_ID_LENGTH];
                    byteBuf.readBytes(streamByte);
                    this.streamId = Common.byteSmallToInt(streamByte); //只有 stream 是小端模式
                    for(byte i : streamByte){
                        headData.add(i);
                    }
                    //System.out.println("streamId === " + streamId);
                }
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
                if(msg.equals("connect")) {
                    Map<String,Object> data = AMFUtil.load_amf_object(amfClass);
                    if(data.containsKey("app")) {
                        String app = data.get("app").toString();
                        if(app.equals(Common.APP_NAME)) {
                            List<Byte> result = new ArrayList<Byte>();
                            byte[] resultString = AMFUtil.writeString("_result");
                            for(byte i: resultString){
                                result.add(i);
                            }
                            byte[] resultNumber = AMFUtil.writeNumber(txid);
                            for(byte i: resultNumber){
                                result.add(i);
                            }
                            Map<String,Object> version = new HashMap<String, Object>();
                            double capabilities = 255.0;
                            double mode = 1.0;
                            version.put("fmsVer","FMS/4,5,1,484");
                            version.put("capabilities",capabilities);
                            version.put("mode",mode);
                            byte[] versionByte = AMFUtil.writeObject(version);
                            for(byte i: versionByte){
                                result.add(i);
                            }
                            Map<String,Object> status = new HashMap<String, Object>();
                            double objectEncoding = 3.0;
                            status.put("level","status");
                            status.put("code","NetConnection.Connect.Success");
                            status.put("description","Connection succeeded.");
                            status.put("objectEncoding",objectEncoding);
                            byte[] statusVersion = AMFUtil.writeObject(status);
                            //System.out.println(Common.bytes2hex(statusVersion));
                            for(byte i: statusVersion){
                                result.add(i);
                            }
                            sendData(result,ctx);
                        }
                    }
                } else if(msg.equals("createStream")) {
                    List<Byte> result = new ArrayList<Byte>();
                    byte[] resultString = AMFUtil.writeString("_result");
                    for(byte i: resultString){
                        result.add(i);
                    }
                    byte[] resultNumber = AMFUtil.writeNumber(txid);
                    for(byte i: resultNumber){
                        result.add(i);
                    }
                    byte[] resultStream = AMFUtil.writeNumber(Common.STREAM_ID);
                    for(byte i: resultStream){
                        result.add(i);
                    }
                    sendData(result,ctx);
                } else if(msg.equals("publish")) {
                    AMFUtil.load_amf(amfClass);
                    String path = AMFUtil.load_amf_string(amfClass); //这个为发布的 url 协议
                    System.out.println(path);
                    Map<String,Object> status = new HashMap<String, Object>();
                    status.put("level","status");
                    status.put("code","NetStream.Publish.Start");
                    status.put("description","Stream is now published.");
                    status.put("details",path);

                    List<Byte> result = new ArrayList<Byte>();
                    for(byte i: AMFUtil.writeString("onStatus")) {
                        result.add(i);
                    }
                    for(byte i: AMFUtil.writeNumber(0.0)){
                        result.add(i);
                    }
                    result.add(AMFUtil.writeNull());
                    for(byte i : AMFUtil.writeObject(status)){
                        result.add(i);
                    }
                    sendData(result,ctx);

                    List<Byte> result2 = new ArrayList<Byte>();
                    byte[] resultString = AMFUtil.writeString("_result");
                    for(byte i: resultString){
                        result2.add(i);
                    }
                    byte[] resultNumber = AMFUtil.writeNumber(txid);
                    for(byte i: resultNumber){
                        result2.add(i);
                    }
                    sendData(result2,ctx);
                }
                break;
            default:
                break;
        }
//       MT =  message[chunkMessageIndex];
//       System.out.println("Mt === " + MT  );
//       chunkMessageIndex = chunkMessageIndex + Common.MESSAGE_MT_LENGTH;
//       byte[] payLoadByte = new byte[Common.MESSAGE_PAYLOAD_LENGTH];
//       System.arraycopy(message,chunkMessageIndex,payLoadByte,0,Common.MESSAGE_PAYLOAD_LENGTH);
//       chunkMessageIndex = chunkMessageIndex + Common.MESSAGE_PAYLOAD_LENGTH;
//       this.payloadLength = Common.byteToInt24(payLoadByte);
//       System.out.println("payloadLength === " + this.payloadLength );
    }

    private void sendData(List<Byte> chunkData,ChannelHandlerContext ctx) {
        List<Byte> rtmpHead = new ArrayList<Byte>();
        byte flags = (3 & 0x3f) | (0 << 6);
        rtmpHead.add(flags);
        byte[] timestamp = {0x00,0x00,0x00};
        for(byte i: timestamp){
            rtmpHead.add(i);
        }
        int msg_len = chunkData.size();
        byte[] msgLength = Common.intToByte(msg_len);
        rtmpHead.add(msgLength[2]);
        rtmpHead.add(msgLength[1]);
        rtmpHead.add(msgLength[0]);
        byte msg_type = 0x14;
        rtmpHead.add(msg_type);
        rtmpHead.add((byte) 0x00);
        rtmpHead.add((byte) 0x00);
        rtmpHead.add((byte) 0x00);
        rtmpHead.add((byte) 0x00);
        List<Byte> chunk = new ArrayList<Byte>();
        for(int i = 0; i < rtmpHead.size(); i++){
            chunk.add(rtmpHead.get(i));
        }
        int pos = 0;
        while(pos < chunkData.size()){
            if(chunkData.size() - pos < Common.DEFAULT_CHUNK_MESSAGE_LENGTH){
                for(int i = pos; i < chunkData.size();i++){
                    chunk.add(chunkData.get(i));
                }
            } else {
                for(int i = pos; i < pos + 128;i++) {
                    chunk.add(chunkData.get(i));
                }
                chunk.add((byte) ((3 & 0x3f) | (3 << 6)));
            }
            pos += Common.DEFAULT_CHUNK_MESSAGE_LENGTH;
        }
        ctx.writeAndFlush(Unpooled.copiedBuffer(Common.conversionByteArray(chunk)));
    }


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


//    /**
//     * 废弃解析chunkMessage 数据
//     * @param chunkData
//     * @param ctx
//     */
//    private void handChunkMessage(List<Byte> chunkData,ChannelHandlerContext ctx) {
//        //  System.out.println(Common.bytes2hex(Common.conversionByteArray(chunkData)));
//        byte flags = chunkData.get(0);
//        int[]  chunk_head_length = {12,8,4,1}; //对应的 chunk head length 长度
//        // byte fmtByte =  (byte)((byte)flags >> 6); //向右移动 6 位 获取 fmt
//        int fmt =  (byte) ((flags & 0xff & 0xff) >> 6);
//        int csidTS = (byte)((flags & 0xff & 0xff) & 0x3f); // 按位与 11 为 1 ，有0 为 0
//
//        try{
//            int head_len = chunk_head_length[fmt];
//            int basic_head_len = chunkHeadIndex = getBasicHeadLength(csidTS);
//            byte[] chunkDataByte = Common.conversionByteArray(chunkData);
//
//            if(head_len >= 4) { // 大于 1 先提取出 timestamp
//                byte[] timestampByte = new byte[Common.TIMESTAMP_BYTE_LENGTH];
//                System.arraycopy(chunkDataByte,chunkHeadIndex,timestampByte,0,Common.TIMESTAMP_BYTE_LENGTH);
//                timestamp =  Common.byteToInt24(timestampByte);
//                if(timestamp == Common.TIMESTAMP_MAX_NUM) {
//                    isExtendedTimestamp = true; // 前3个字节放不下，放在最后面的四个字节
//                }
//                //   System.out.println("timestamp == " + timestamp);
//                chunkHeadIndex = chunkHeadIndex + Common.TIMESTAMP_BYTE_LENGTH;
//            }
//            if(head_len >= 8) { // 大于 4 先提取出 msgLength
//                byte[] msg_len = new byte[Common.TIMESTAMP_BYTE_LENGTH];
//                System.arraycopy(chunkDataByte,chunkHeadIndex,msg_len,0,Common.MSG_LEN_LENGTH);
//                this.msgLength = Common.byteToInt24(msg_len);
//                this.allMsglength = Common.byteToInt24(msg_len);
//                if(this.msgLength > chunkData.size()) { //后面分包的情况,这方法可能有问题
//                    //   System.out.println("101 数据不全");
//                    //System.out.println(Common.bytes2hex(Common.conversionByteArray(chunkData)));
//                    //ctx.close();
//                }
//                chunkHeadIndex = chunkHeadIndex + Common.TIMESTAMP_BYTE_LENGTH;
//
//                this.msgType = chunkDataByte[chunkHeadIndex];
//                chunkHeadIndex = chunkHeadIndex + Common.MST_TYPE_LENGTH;
//                System.out.println("消息类型 === " + Integer.toHexString(this.msgType) + " msg len " + this.msgLength + " time " + timestamp);
//            }
//
//            if(head_len >= 12) {
//                byte[] streamByte = new byte[Common.STREAM_ID_LENGTH];
//                System.arraycopy(chunkDataByte,chunkHeadIndex,streamByte,0,Common.STREAM_ID_LENGTH);
//                this.streamId = Common.byteSmallToInt(streamByte); //只有 stream 是小端模式
//                //System.out.println("streamId === " + streamId);
//                chunkHeadIndex = chunkHeadIndex + Common.STREAM_ID_LENGTH;
//            }
//            if(isExtendedTimestamp) {
//                byte[] timestampByte = new byte[Common.EXTEND_TIMESTAMP_LENGTH];
//                System.arraycopy(chunkDataByte,chunkHeadIndex,timestampByte,0,Common.EXTEND_TIMESTAMP_LENGTH);
//                this.timestamp = Common.byteToInt24(timestampByte);
//                chunkHeadIndex = chunkHeadIndex + Common.EXTEND_TIMESTAMP_LENGTH;
//            }
//            int msgIndex = msgLength > Common.DEFAULT_CHUNK_MESSAGE_LENGTH ? chunkHeadIndex + Common.DEFAULT_CHUNK_MESSAGE_LENGTH : chunkHeadIndex + msgLength;
//            if(chunkData.size() < msgIndex){
//                return;
//            }
//            for(int i = chunkHeadIndex;i < msgIndex; i++) {
//                chunkMessage.add(chunkData.get(i));
//            }
//            if(chunkMessage.size() < allMsglength){ //还没有提取完所有数据
//                msgLength = allMsglength - chunkMessage.size();
//            } else {
//                handMessage(Common.conversionByteArray(chunkMessage),ctx);
//                isExtendedTimestamp = false;
//                chunkMessage = new ArrayList<Byte>();
//            }
//            chunkHeadIndex = 0;
//            chunkData = Common.removeList(chunkData,0,msgIndex - 1); // 如果chunkData 还有数据，粘包了，那么解析就好了
//            if(chunkData.size() > 0) { //如果还有数据，那么继续解析就好了
//                try {
//                    handChunkMessage(chunkData,ctx);
//                }catch (Exception e) {
//                    return ;
//                }
//            } else {
//                setChunkData(ctx);
//            }
//        }catch (Exception e) {
//            ctx.close();
//            e.printStackTrace();
//            System.err.println(Common.bytes2hex(Common.conversionByteArray(chunkData)));
//        }
//
//    }
//
//    private void setChunkData(ChannelHandlerContext ctx) {
//        if(chunkData.size() >= Common.READ_CHUNK_LENGTH) {
//            handChunkMessage(chunkData,ctx);
//            return;
//        }
//        int length = byteBuf.readableBytes() > Common.READ_CHUNK_LENGTH ? Common.READ_CHUNK_LENGTH : byteBuf.readableBytes();
//        if(Common.READ_CHUNK_LENGTH - chunkData.size() <length) {
//            length = Common.READ_CHUNK_LENGTH - chunkData.size();
//        }
//        if(length > 0) {
//            byte[] data = new byte[length];
//            byteBuf.readBytes(data);
//            for(byte i: data){
//                chunkData.add(i);
//            }
//            handChunkMessage(chunkData,ctx);
//        }
//    }



//    /**
//     * rtmp 握手数据判断
//     * @param ctx
//     */
//    private void handshake(ChannelHandlerContext ctx){
//        if(handshakeData.size() >= Common.C0_LENGTH && !isSendS1){
//            byte c0 = handshakeData.get(Common.C0_INDEX);
//            if(c0 != Common.C0){ //如果 c0 错误，那么关闭连接
//                ctx.close();
//                return;
//            } else {
//                int time = (int) (new Date().getTime() / 1000);
//                byte[] timeByte = Common.intToByte(time);
//                ctx.writeAndFlush(Unpooled.copiedBuffer(new byte[]{Common.S0}));
//                for(byte i : timeByte){
//                    S1.add(i);
//                }
//                for(byte i: zero){
//                    S1.add(i);
//                }
//                for(int i = 0; i < Common.RANDOM_LENGTH;i++) {
//                    Random random = new Random();
//                    S1.add((byte) random.nextInt(9));
//                }
//                ctx.writeAndFlush(Unpooled.copiedBuffer(Common.conversionByteArray(S1)));
//                isSendS1 = true;
//            }
//        }
//
//        if(handshakeData.size() >= (Common.C0_LENGTH + Common.C1_LENGTH)){ // 服务端接收 c1 完毕，开始发送s2
//            for(int i = 1;i <= 4; i++) {  //提取 c1 的 time
//                S2.add(handshakeData.get(i));
//            }
//            int time = (int) (new Date().getTime() / 1000); //设置 s2 time
//            byte[] timeByte = Common.intToByte(time);
//            for(byte i : timeByte){
//                S2.add(i);
//            }
//            for(int i = 8; i < Common.C1_LENGTH;i++) {
//                S2.add(handshakeData.get(i));
//            }
//            ctx.writeAndFlush(Unpooled.copiedBuffer(Common.conversionByteArray(S2)));
//        }
//        if(handshakeData.size() >= Common.HANDSHAKE_LENGTH) {
//            isHandshake = true;
////            System.out.println("S1");
////            byte[] s1 = Common.conversionByteArray(S1);
////            System.out.println(s1.length);
////            System.out.println(Common.bytes2hex(s1));;
//////            byte[] s2 = Common.conversionByteArray(S2);
//////            System.out.println("S2");
//////            System.out.println(Common.bytes2hex(s2));
////
////            List<Byte> c1List = new ArrayList<Byte>();
////            List<Byte> c2List = new ArrayList<Byte>();
////
////            for(int i = 1;i < 1537;i++ ) {
////                c1List.add(handshakeData.get(i));
////            }
////            for(int i = 1537;i < handshakeData.size();i++) {
////                c2List.add(handshakeData.get(i));
////            }
//
////            System.out.println("接受到的握手数据");
////            System.out.println(handshakeData.size());
////            System.out.println("C1");
////            System.out.println(Common.bytes2hex(Common.conversionByteArray(c1List)));
////            System.out.println("C2");
////            System.out.println(c2List.size());
////            System.out.println(Common.bytes2hex(Common.conversionByteArray(c2List)));
////
////            System.out.println(Common.bytes2hex(Common.conversionByteArray(handshakeData)));
//
//        }
//    }
}